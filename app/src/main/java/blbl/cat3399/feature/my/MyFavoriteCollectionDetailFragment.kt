package blbl.cat3399.feature.my

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import blbl.cat3399.R
import blbl.cat3399.core.api.BiliApi
import blbl.cat3399.core.log.AppLog
import blbl.cat3399.core.model.FollowedUgcCollection
import blbl.cat3399.core.model.VideoCard
import blbl.cat3399.core.net.BiliClient
import blbl.cat3399.core.ui.AppToast
import blbl.cat3399.core.ui.BackButtonSizingHelper
import blbl.cat3399.core.ui.UiScale
import blbl.cat3399.core.ui.smoothScrollToPositionStart
import blbl.cat3399.databinding.ActivityVideoDetailBinding
import blbl.cat3399.feature.player.VideoCardPlaylistPage
import blbl.cat3399.feature.player.FollowedCollectionPlaybackSource
import blbl.cat3399.feature.video.VideoDetailHeaderAdapter
import blbl.cat3399.feature.video.buildPagedVideoCardPlaybackHandle
import blbl.cat3399.feature.video.openVideoFromPlaybackHandle
import blbl.cat3399.ui.RefreshKeyHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class MyFollowedCollectionDetailFragment : Fragment(), RefreshKeyHandler {
    private var _binding: ActivityVideoDetailBinding? = null
    private val binding get() = _binding!!

    private lateinit var headerAdapter: VideoDetailHeaderAdapter
    private val cards = ArrayList<VideoCard>()

    private val collection by lazy {
        val args = requireArguments()
        FollowedUgcCollection(
            viewerMid = args.getLong(ARG_VIEWER_MID),
            seasonId = args.getLong(ARG_SEASON_ID),
            ownerMid = args.getLong(ARG_OWNER_MID),
            ownerName = args.getString(ARG_OWNER_NAME),
            title = args.getString(ARG_TITLE).orEmpty(),
            coverUrl = args.getString(ARG_COVER_URL),
            description = args.getString(ARG_DESCRIPTION),
            videoCount = args.getInt(ARG_VIDEO_COUNT).takeIf { it > 0 },
        )
    }
    private val loadedStableKeys = HashSet<String>()
    private var episodeOrderReversed = false
    private var isLoadingMore = false
    private var endReached = false
    private var page = 1
    private var requestToken = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = ActivityVideoDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.btnBack.setOnClickListener { parentFragmentManager.popBackStackImmediate() }
        headerAdapter =
            VideoDetailHeaderAdapter(
                onPlayClick = { onPrimaryActionClick() },
                onUpClick = {},
                onTabClick = {},
                onTagClick = {},
                onLikeClick = {},
                onLikeLongPress = {},
                onCoinClick = {},
                onFavClick = {},
                onSecondaryClick = {},
                onPrimaryActionFocused = { smoothScrollHeaderToTop() },
                onSecondaryActionFocused = { smoothScrollHeaderToTop() },
                onPartsOrderClick = {
                    episodeOrderReversed = !episodeOrderReversed
                    refreshHeader()
                    binding.recycler.post { headerAdapter.requestFocusPartsOrder() }
                },
                onSeasonOrderClick = {},
                onPartCardClick = { card, _ ->
                    val position = cards.indexOfFirst { it.bvid == card.bvid }
                    if (position >= 0) openVideoAtPosition(position)
                },
                onSeasonCardClick = { _, _ -> },
                onPartsNearEnd = { if (!isLoadingMore && !endReached) loadNextPage() },
            )
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        (binding.recycler.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        binding.recycler.adapter = headerAdapter
        binding.swipeRefresh.setOnRefreshListener { resetAndLoad() }
        refreshHeader()

        if (savedInstanceState == null) {
            binding.swipeRefresh.isRefreshing = true
            resetAndLoad()
        }
        binding.recycler.post { headerAdapter.requestFocusPlay() }
    }

    override fun onResume() {
        super.onResume()
        applyBackButtonSizing()
        if (this::headerAdapter.isInitialized) refreshHeader()
    }

    private fun smoothScrollHeaderToTop() {
        val recycler = _binding?.recycler ?: return
        if (!recycler.canScrollVertically(-1)) return
        recycler.smoothScrollToPositionStart(0)
    }

    private fun applyBackButtonSizing() {
        BackButtonSizingHelper.applySidebarSizing(
            view = binding.btnBack,
            resources = resources,
            sidebarScale = UiScale.factor(requireContext()),
        )
    }

    override fun handleRefreshKey(): Boolean {
        val b = _binding ?: return false
        if (!isResumed) return false
        if (b.swipeRefresh.isRefreshing) return true
        b.swipeRefresh.isRefreshing = true
        resetAndLoad()
        return true
    }

    private fun resetAndLoad() {
        loadedStableKeys.clear()
        isLoadingMore = false
        endReached = false
        page = 1
        requestToken++
        cards.clear()
        if (this::headerAdapter.isInitialized) refreshHeader()
        loadNextPage(isRefresh = true)
    }

    private fun loadNextPage(isRefresh: Boolean = false) {
        if (isLoadingMore || endReached) return
        val token = requestToken
        isLoadingMore = true
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                fetchNextPage(isRefresh, token)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                AppLog.e("MyCollectionDetail", "load failed mid=${collection.ownerMid} seasonId=${collection.seasonId}", t)
                context?.let { AppToast.show(it, getString(R.string.my_collection_load_failed)) }
            } finally {
                isLoadingMore = false
                if (token == requestToken) {
                    _binding?.swipeRefresh?.isRefreshing = false
                    refreshHeader()
                }
            }
        }
    }

    private suspend fun fetchNextPage(isRefresh: Boolean, token: Int) {
        val response =
            BiliApi.followedSeasonVideos(
                seasonId = collection.seasonId,
                ownerMid = collection.ownerMid,
                ownerName = collection.ownerName,
                pn = page,
                ps = PAGE_SIZE,
            )
        if (token != requestToken) return
        val fresh = response.items.filter { loadedStableKeys.add(it.stableKey()) }
        if (isRefresh) cards.clear()
        cards.addAll(fresh)
        endReached = response.items.isEmpty() || !response.hasMore
        if (!endReached) page++
        if (this::headerAdapter.isInitialized) refreshHeader()
    }

    private fun buildCollectionMeta(): String {
        val owner = collection.ownerName?.takeIf { it.isNotBlank() } ?: getString(R.string.my_collection_unknown_owner)
        val count = collection.videoCount
        return if (count != null) {
            getString(R.string.my_collection_owner_count_meta_fmt, owner, count)
        } else {
            getString(R.string.my_collection_owner_meta_fmt, owner)
        }
    }

    private fun refreshHeader() {
        if (_binding == null || !this::headerAdapter.isInitialized) return
        val resumeBvid = BiliClient.prefs.followedCollectionLastBvid(collection.viewerMid, collection.ownerMid, collection.seasonId)
        val episodeCount = collection.videoCount ?: cards.size.takeIf { endReached }
        val partsHeader =
            episodeCount?.let {
                getString(R.string.my_episode_section_count_fmt, getString(R.string.my_episode_section), it)
            } ?: getString(R.string.my_episode_section)
        val displayCards = if (episodeOrderReversed) cards.asReversed() else cards.toList()
        headerAdapter.update(
            title = collection.title.ifBlank { getString(R.string.my_collection_untitled) },
            metaText = buildCollectionMeta(),
            desc = collection.description,
            coverUrl = collection.coverUrl,
            usePosterCover = true,
            upName = null,
            upAvatar = null,
            tabName = null,
            tags = emptyList(),
            primaryButtonText = getString(if (resumeBvid != null) R.string.my_btn_continue else R.string.my_btn_play_first),
            secondaryButtonText = getString(R.string.my_btn_followed),
            showActions = false,
            partsHeaderText = partsHeader,
            partsCards = displayCards,
            partsSelectedKey = null,
            partsOrderReversed = episodeOrderReversed,
            partsAutoScrollToSelected = false,
            partsScrollToStart = false,
            seasonHeaderText = null,
            seasonCards = emptyList(),
            seasonSelectedKey = null,
            seasonOrderReversed = false,
            seasonAutoScrollToSelected = false,
            seasonScrollToStart = false,
            recommendHeaderText = null,
        )
    }

    private fun onPrimaryActionClick() {
        if (BiliClient.prefs.followedCollectionLastBvid(collection.viewerMid, collection.ownerMid, collection.seasonId) != null) {
            continueFromLastPlayed()
            return
        }
        if (cards.isEmpty()) return
        openVideoAtPosition(0)
    }

    private fun openVideoAtPosition(position: Int) {
        requireContext().openVideoFromPlaybackHandle(
            playbackHandle = playbackHandle(),
            position = position,
            openDetailBeforePlay = BiliClient.prefs.playerOpenDetailBeforePlay,
        )
    }

    private fun continueFromLastPlayed() {
        val resumeBvid =
            BiliClient.prefs.followedCollectionLastBvid(
                viewerMid = collection.viewerMid,
                ownerMid = collection.ownerMid,
                seasonId = collection.seasonId,
            ) ?: return
        if (isLoadingMore) return

        val token = requestToken
        isLoadingMore = true
        binding.swipeRefresh.isRefreshing = true
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                var searchedPages = 0
                while (
                    token == requestToken &&
                        cards.none { it.bvid == resumeBvid } &&
                        !endReached &&
                        searchedPages < MAX_RESUME_SEARCH_PAGES
                ) {
                    val pageBefore = page
                    fetchNextPage(isRefresh = false, token = token)
                    searchedPages++
                    if (page == pageBefore && !endReached) break
                }
                if (token != requestToken) return@launch

                val position = cards.indexOfFirst { it.bvid == resumeBvid }
                if (position >= 0) {
                    openVideoAtPosition(position)
                } else {
                    AppToast.show(requireContext(), getString(R.string.my_collection_resume_not_found))
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                AppLog.e("MyCollectionDetail", "resume lookup failed seasonId=${collection.seasonId}", t)
                context?.let { AppToast.show(it, getString(R.string.my_collection_load_failed)) }
            } finally {
                isLoadingMore = false
                if (token == requestToken) {
                    _binding?.swipeRefresh?.isRefreshing = false
                    refreshHeader()
                }
            }
        }
    }

    private fun playbackHandle() =
        buildPagedVideoCardPlaybackHandle(
            source = FollowedCollectionPlaybackSource(
                viewerMid = collection.viewerMid,
                ownerMid = collection.ownerMid,
                seasonId = collection.seasonId,
            ).encode(),
            cardsProvider = { cards.toList() },
            nextCursorProvider = { page },
            hasMoreProvider = { !endReached },
        ) { targetPage ->
            val pageNum = targetPage.coerceAtLeast(1)
            val result =
                BiliApi.followedSeasonVideos(
                    seasonId = collection.seasonId,
                    ownerMid = collection.ownerMid,
                    ownerName = collection.ownerName,
                    pn = pageNum,
                    ps = PAGE_SIZE,
                )
            val hasMore = result.items.isNotEmpty() && result.hasMore
            VideoCardPlaylistPage(
                cards = result.items,
                nextCursor = pageNum + 1,
                hasMore = hasMore,
                canAdvance = hasMore && result.items.isNotEmpty(),
            )
        }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_SEASON_ID = "season_id"
        private const val ARG_VIEWER_MID = "viewer_mid"
        private const val ARG_OWNER_MID = "owner_mid"
        private const val ARG_OWNER_NAME = "owner_name"
        private const val ARG_TITLE = "title"
        private const val ARG_COVER_URL = "cover_url"
        private const val ARG_DESCRIPTION = "description"
        private const val ARG_VIDEO_COUNT = "video_count"
        private const val PAGE_SIZE = 20
        private const val MAX_RESUME_SEARCH_PAGES = 500

        fun newInstance(collection: FollowedUgcCollection): MyFollowedCollectionDetailFragment =
            MyFollowedCollectionDetailFragment().apply {
                arguments =
                    Bundle().apply {
                        putLong(ARG_VIEWER_MID, collection.viewerMid)
                        putLong(ARG_SEASON_ID, collection.seasonId)
                        putLong(ARG_OWNER_MID, collection.ownerMid)
                        putString(ARG_OWNER_NAME, collection.ownerName)
                        putString(ARG_TITLE, collection.title)
                        putString(ARG_COVER_URL, collection.coverUrl)
                        putString(ARG_DESCRIPTION, collection.description)
                        putInt(ARG_VIDEO_COUNT, collection.videoCount ?: -1)
                    }
            }
    }
}