package blbl.cat3399.feature.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import blbl.cat3399.R
import blbl.cat3399.core.bangumi.BangumiCalendarHttpApi
import blbl.cat3399.core.bangumi.BangumiCalendarPeriod
import blbl.cat3399.core.bangumi.BangumiCalendarRepository
import blbl.cat3399.core.bangumi.BangumiCalendarSnapshot
import blbl.cat3399.core.bangumi.SharedPreferencesBangumiCalendarCache
import blbl.cat3399.core.log.AppLog
import blbl.cat3399.core.net.BiliClient
import blbl.cat3399.core.ui.AppToast
import blbl.cat3399.core.ui.DpadGridController
import blbl.cat3399.core.ui.TabContentSwitchFocusHost
import blbl.cat3399.core.ui.TabSwitchFocusTarget
import blbl.cat3399.core.ui.postIfAttached
import blbl.cat3399.core.ui.requestFocusAdapterPositionReliable
import blbl.cat3399.databinding.FragmentBangumiCalendarBinding
import blbl.cat3399.ui.RefreshKeyHandler
import blbl.cat3399.ui.SearchNavigationHost
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.Calendar

class BangumiCalendarFragment : Fragment(), RefreshKeyHandler, TabSwitchFocusTarget {
    private var _binding: FragmentBangumiCalendarBinding? = null
    private val binding get() = _binding!!

    private val repository: BangumiCalendarRepository by lazy {
        BangumiCalendarRepository(
            api = BangumiCalendarHttpApi(),
            cache = SharedPreferencesBangumiCalendarCache(requireContext().applicationContext),
        )
    }

    private val quarterButtons
        get() =
            listOf(
                binding.btnQuarter1,
                binding.btnQuarter2,
                binding.btnQuarter3,
                binding.btnQuarter4,
            )

    private val navigationState =
        Calendar.getInstance().let { calendar ->
            BangumiCalendarNavigationState(
                BangumiCalendarPeriod(
                    year = calendar.get(Calendar.YEAR),
                    quarter = calendar.get(Calendar.MONTH) / 3 + 1,
                ),
            )
        }
    private var snapshot: BangumiCalendarSnapshot? = null
    private var dpadGridController: DpadGridController? = null
    private var requestToken = 0
    private var isLoadingInitial = false
    private var isLoadingMore = false
    private var displayedPeriod: BangumiCalendarPeriod? = null
    private var pendingGridFocusPosition: Int? = null

    private lateinit var adapter: BangumiCalendarAdapter

    private val period: BangumiCalendarPeriod
        get() = navigationState.period

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentBangumiCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        if (!::adapter.isInitialized) {
            adapter =
                BangumiCalendarAdapter { position, subject ->
                    openInAppSearch(position, subject.title)
                }
        }

        binding.recycler.adapter = adapter
        binding.recycler.setHasFixedSize(true)
        binding.recycler.layoutManager = GridLayoutManager(requireContext(), spanCount())
        (binding.recycler.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        binding.recycler.clearOnScrollListeners()
        quarterButtons.forEachIndexed { index, button ->
            button.isCheckable = true
            button.setOnClickListener { selectQuarter(index + 1) }
        }
        binding.btnPreviousYear.setOnClickListener { selectYear(period.year - 1) }
        binding.btnNextYear.setOnClickListener { selectYear(period.year + 1) }

        dpadGridController?.release()
        dpadGridController =
            DpadGridController(
                recyclerView = binding.recycler,
                callbacks =
                    object : DpadGridController.Callbacks {
                        override fun onTopEdge(): Boolean {
                            return focusSelectedQuarter()
                        }

                        override fun onLeftEdge(): Boolean = switchToPreviousHomeTab()

                        override fun onRightEdge() {
                            switchToNextHomeTab()
                        }

                        override fun canLoadMore(): Boolean = snapshot?.hasNext == true

                        override fun loadMore() {
                            loadNextPage()
                        }
                    },
                config =
                    DpadGridController.Config(
                        isEnabled = { _binding != null && isResumed },
                    ),
            ).also { it.install() }

        binding.recycler.addOnScrollListener(
            object : RecyclerView.OnScrollListener() {
                override fun onScrolled(
                    recyclerView: RecyclerView,
                    dx: Int,
                    dy: Int,
                ) {
                    if (dy <= 0 || isLoadingMore || snapshot?.hasNext != true) return
                    val layoutManager = recyclerView.layoutManager as? GridLayoutManager ?: return
                    val lastVisible = layoutManager.findLastVisibleItemPosition()
                    if (adapter.itemCount > 0 && adapter.itemCount - lastVisible - 1 <= 8) {
                        loadNextPage()
                    }
                }
            },
        )
        binding.swipeRefresh.setOnRefreshListener { loadCurrentPeriod(forceRefresh = true) }
        renderPeriodControls()
        loadCurrentPeriod()
    }

    override fun onResume() {
        super.onResume()
        (binding.recycler.layoutManager as? GridLayoutManager)?.spanCount = spanCount()
        navigationState.takePendingSearchReturnPosition()?.let {
            pendingGridFocusPosition = it
        }
        consumePendingGridFocus()
    }

    override fun handleRefreshKey(): Boolean {
        val currentBinding = _binding ?: return false
        if (!isResumed) return false
        if (currentBinding.swipeRefresh.isRefreshing) return true
        loadCurrentPeriod(forceRefresh = true)
        return true
    }

    override fun requestFocusFirstCardFromTab(): Boolean = requestGridFocus(0)

    override fun requestFocusFirstCardFromContentSwitch(): Boolean =
        requestGridFocus(navigationState.focusPositionForContentSwitch())

    override fun requestFocusPrimaryItemFromTab(): Boolean = requestFocusFirstCardFromTab()

    override fun requestFocusPrimaryItemFromContentSwitch(): Boolean = requestFocusFirstCardFromContentSwitch()

    override fun requestFocusPrimaryItemFromBackToTab0(): Boolean = requestGridFocus(0)

    private fun selectQuarter(quarter: Int) {
        if (quarter !in 1..4) return
        if (!navigationState.selectQuarter(quarter)) {
            renderPeriodControls()
            return
        }
        renderPeriodControls()
        loadCurrentPeriod()
    }

    private fun selectYear(year: Int) {
        if (!navigationState.selectYear(year, Calendar.getInstance().get(Calendar.YEAR))) return
        renderPeriodControls()
        loadCurrentPeriod()
    }

    private fun renderPeriodControls() {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        binding.tvYear.text = getString(R.string.bangumi_calendar_year_format, period.year)
        binding.btnPreviousYear.isEnabled = period.year > 1
        binding.btnNextYear.isEnabled = period.year < currentYear
        quarterButtons.forEachIndexed { index, button ->
            button.isChecked = index + 1 == period.quarter
        }
    }

    private fun loadCurrentPeriod(forceRefresh: Boolean = false) {
        val selectedPeriod = period
        val token = ++requestToken
        isLoadingMore = false
        dpadGridController?.clearPendingFocusAfterLoadMore()
        val cached = repository.cached(selectedPeriod)
        val hasSameDisplayedItems =
            displayedPeriod == selectedPeriod &&
                snapshot?.items == cached?.items
        snapshot = cached
        displayedPeriod = selectedPeriod
        if (!hasSameDisplayedItems) adapter.submit(cached?.items.orEmpty())
        updateEmptyState(cached)
        val cacheIsFresh = cached?.let(repository::isFresh) == true
        if (cacheIsFresh && !forceRefresh) {
            binding.swipeRefresh.isRefreshing = false
            consumePendingGridFocus()
            return
        }

        isLoadingInitial = true
        binding.swipeRefresh.isRefreshing = true
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val result = repository.loadInitial(selectedPeriod, forceRefresh)
                if (token != requestToken || selectedPeriod != period) return@launch
                val previousItems = snapshot?.items
                snapshot = result.snapshot
                if (previousItems != result.snapshot.items) {
                    adapter.submit(result.snapshot.items)
                }
                updateEmptyState(result.snapshot, loadFailed = result.usedCacheFallback)
                if (result.usedCacheFallback) {
                    AppToast.show(requireContext(), getString(R.string.bangumi_calendar_refresh_failed))
                }
                consumePendingGridFocus()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (token != requestToken || selectedPeriod != period) return@launch
                AppLog.e("BangumiCalendar", "load failed period=${selectedPeriod.cacheKey}", error)
                updateEmptyState(snapshot, loadFailed = true)
                AppToast.show(requireContext(), getString(R.string.bangumi_calendar_load_failed))
            } finally {
                if (token == requestToken) {
                    isLoadingInitial = false
                    _binding?.swipeRefresh?.isRefreshing = false
                }
            }
        }
    }

    private fun loadNextPage() {
        val selectedPeriod = period
        val current = snapshot ?: return
        if (!current.hasNext || isLoadingMore || isLoadingInitial) return
        val token = requestToken
        isLoadingMore = true
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val updated = repository.loadMore(selectedPeriod, current)
                if (token != requestToken || selectedPeriod != period) return@launch
                val existingIds = current.items.mapTo(HashSet()) { it.id }
                val appended = updated.items.filterNot { it.id in existingIds }
                snapshot = updated
                adapter.append(appended)
                updateEmptyState(updated)
                consumePendingGridFocus()
                dpadGridController?.consumePendingFocusAfterLoadMore()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (token != requestToken || selectedPeriod != period) return@launch
                AppLog.e("BangumiCalendar", "load more failed period=${selectedPeriod.cacheKey}", error)
                dpadGridController?.clearPendingFocusAfterLoadMore()
                AppToast.show(requireContext(), getString(R.string.bangumi_calendar_load_failed))
            } finally {
                if (token == requestToken) isLoadingMore = false
            }
        }
    }

    private fun updateEmptyState(
        snapshot: BangumiCalendarSnapshot?,
        loadFailed: Boolean = false,
    ) {
        val emptyView = _binding?.tvEmpty ?: return
        when (bangumiCalendarEmptyState(snapshot, loadFailed)) {
            BangumiCalendarEmptyState.HIDDEN -> emptyView.visibility = View.GONE
            BangumiCalendarEmptyState.NO_RESULTS -> {
                emptyView.setText(R.string.bangumi_calendar_empty)
                emptyView.visibility = View.VISIBLE
            }

            BangumiCalendarEmptyState.LOAD_FAILED -> {
                emptyView.setText(R.string.bangumi_calendar_load_failed)
                emptyView.visibility = View.VISIBLE
            }
        }
    }

    private fun spanCount(): Int = BiliClient.prefs.pgcGridSpanCount.coerceIn(1, 6)

    private fun openInAppSearch(
        position: Int,
        keyword: String,
    ) {
        val term = keyword.trim()
        if (term.isBlank()) return
        navigationState.searchOpenedFromCard(position)
        val host = activity as? SearchNavigationHost
        if (host == null) {
            navigationState.cancelPendingSearchReturn()
            AppLog.e("BangumiCalendar", "unable to open search: activity does not host in-app search")
            AppToast.show(requireContext(), getString(R.string.bangumi_calendar_search_unavailable))
            return
        }
        host.openSearchForKeyword(term)
    }

    private fun requestGridFocus(position: Int): Boolean {
        pendingGridFocusPosition = position.coerceAtLeast(0)
        consumePendingGridFocus()
        return true
    }

    private fun consumePendingGridFocus(): Boolean {
        val position = pendingGridFocusPosition ?: return false
        val currentBinding = _binding ?: return false
        if (!isResumed) return false
        if (adapter.itemCount <= 0) {
            currentBinding.recycler.requestFocus()
            return true
        }
        val target = position.coerceIn(0, adapter.itemCount - 1)
        currentBinding.recycler.requestFocusAdapterPositionReliable(
            position = target,
            smoothScroll = false,
            isAlive = { _binding === currentBinding && isResumed },
            onFocused = {
                navigationState.rememberFocusedCard(target)
                if (pendingGridFocusPosition == position) pendingGridFocusPosition = null
            },
        )
        return true
    }

    private fun focusSelectedQuarter(): Boolean {
        val button = quarterButtons.getOrNull(period.quarter - 1) ?: return false
        return button.requestFocus()
    }

    private fun captureCurrentFocusedPosition() {
        val recycler = _binding?.recycler ?: return
        val focused = activity?.currentFocus ?: return
        recycler.findContainingViewHolder(focused)
            ?.bindingAdapterPosition
            ?.takeIf { it != RecyclerView.NO_POSITION }
            ?.let(navigationState::rememberFocusedCard)
    }

    private fun selectedHomeTabLayout() =
        parentFragment?.view?.findViewById<com.google.android.material.tabs.TabLayout?>(R.id.tab_layout)

    private fun switchToPreviousHomeTab(): Boolean {
        val tabLayout = selectedHomeTabLayout() ?: return false
        val current = tabLayout.selectedTabPosition.takeIf { it >= 0 } ?: 0
        val previous = current - 1
        if (previous < 0) return false
        captureCurrentFocusedPosition()
        tabLayout.getTabAt(previous)?.select() ?: return false
        tabLayout.postIfAttached {
            (parentFragment as? TabContentSwitchFocusHost)?.requestFocusCurrentPagePrimaryItemFromContentSwitch()
        }
        return true
    }

    private fun switchToNextHomeTab() {
        val tabLayout = selectedHomeTabLayout() ?: return
        val current = tabLayout.selectedTabPosition.takeIf { it >= 0 } ?: 0
        val next = current + 1
        if (next >= tabLayout.tabCount) return
        captureCurrentFocusedPosition()
        tabLayout.getTabAt(next)?.select() ?: return
        tabLayout.postIfAttached {
            (parentFragment as? TabContentSwitchFocusHost)?.requestFocusCurrentPagePrimaryItemFromContentSwitch()
        }
    }

    override fun onDestroyView() {
        requestToken++
        dpadGridController?.release()
        dpadGridController = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        fun newInstance(): BangumiCalendarFragment = BangumiCalendarFragment()
    }
}
