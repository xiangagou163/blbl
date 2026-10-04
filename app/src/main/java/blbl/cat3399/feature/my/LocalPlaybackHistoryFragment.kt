package blbl.cat3399.feature.my

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import blbl.cat3399.core.history.PlaybackHistoryMode
import blbl.cat3399.core.history.PlaybackHistoryPolicy
import blbl.cat3399.core.history.PlaybackHistoryRecord
import blbl.cat3399.core.net.BiliClient
import blbl.cat3399.core.ui.AppToast
import blbl.cat3399.databinding.FragmentLocalPlaybackHistoryBinding
import blbl.cat3399.feature.player.PlayerActivity
import blbl.cat3399.ui.RefreshKeyHandler

class LocalPlaybackHistoryFragment : Fragment(), MyTabSwitchFocusTarget, RefreshKeyHandler {
    private var _binding: FragmentLocalPlaybackHistoryBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: LocalPlaybackHistoryAdapter
    private var pendingFocusFirstItemFromTabSwitch = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLocalPlaybackHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        adapter =
            LocalPlaybackHistoryAdapter(
                onOpen = ::openRecord,
                onDelete = ::deleteRecord,
            )
        binding.recycler.layoutManager = LinearLayoutManager(requireContext())
        binding.recycler.adapter = adapter
        binding.btnClearHistory.setOnClickListener { confirmClearHistory() }
        refreshHistory()
    }

    override fun onResume() {
        super.onResume()
        refreshHistory()
        if (pendingFocusFirstItemFromTabSwitch) focusFirstItem()
    }

    override fun onDestroyView() {
        pendingFocusFirstItemFromTabSwitch = false
        _binding = null
        super.onDestroyView()
    }

    override fun handleRefreshKey(): Boolean {
        if (!isResumed) return false
        refreshHistory()
        return true
    }

    override fun requestFocusFirstItemFromTabSwitch(): Boolean {
        pendingFocusFirstItemFromTabSwitch = true
        if (!isResumed || _binding == null) return true
        return focusFirstItem()
    }

    private fun refreshHistory() {
        val mode = BiliClient.prefs.playbackHistoryMode
        val canReadLocalHistory = PlaybackHistoryPolicy.forMode(mode).readLocalHistory
        val records =
            if (canReadLocalHistory) {
                BiliClient.prefs.localPlaybackHistory.records()
            } else {
                emptyList()
            }
        adapter.submit(records)

        binding.btnClearHistory.visibility = if (canReadLocalHistory && records.isNotEmpty()) View.VISIBLE else View.GONE
        binding.recycler.visibility = if (records.isNotEmpty()) View.VISIBLE else View.GONE
        binding.tvStatus.visibility = if (records.isEmpty()) View.VISIBLE else View.GONE
        binding.tvStatus.text =
            when {
                !canReadLocalHistory && mode == PlaybackHistoryMode.PRIVATE ->
                    "完全无痕模式下不读取本地历史。已有记录会保留；切换到本地模式后可继续查看。"
                !canReadLocalHistory ->
                    "当前为服务端记录模式。本地历史不会被读取；切换到本地模式后可查看本机记录。"
                else -> "暂无本地观看记录"
            }
    }

    private fun deleteRecord(
        record: PlaybackHistoryRecord,
        position: Int,
    ) {
        if (BiliClient.prefs.playbackHistoryMode != PlaybackHistoryMode.LOCAL_ONLY) return
        BiliClient.prefs.localPlaybackHistory.delete(record.workId, record.episodeId)
        refreshHistory()
        binding.recycler.post {
            if (adapter.itemCount == 0) {
                binding.tvStatus.requestFocus()
            } else {
                binding.recycler.scrollToPosition(position.coerceAtMost(adapter.itemCount - 1))
                binding.recycler.post { adapter.requestFocusOpenAt(position.coerceAtMost(adapter.itemCount - 1)) }
            }
        }
    }

    private fun confirmClearHistory() {
        if (BiliClient.prefs.playbackHistoryMode != PlaybackHistoryMode.LOCAL_ONLY) return
        AlertDialog.Builder(requireContext())
            .setTitle("清空本地历史")
            .setMessage("确定删除本机保存的全部观看记录吗？此操作无法撤销。")
            .setNegativeButton("取消", null)
            .setPositiveButton("清空") { _, _ ->
                if (BiliClient.prefs.playbackHistoryMode == PlaybackHistoryMode.LOCAL_ONLY) {
                    BiliClient.prefs.localPlaybackHistory.clear()
                    refreshHistory()
                    binding.tvStatus.requestFocus()
                }
            }
            .show()
    }

    private fun openRecord(record: PlaybackHistoryRecord) {
        if (BiliClient.prefs.playbackHistoryMode != PlaybackHistoryMode.LOCAL_ONLY) return
        val resumableRecord =
            BiliClient.prefs.localPlaybackHistory.resumeRecord(
                workId = record.workId,
                episodeId = record.episodeId,
            ) ?: run {
                refreshHistory()
                return
            }
        if (resumableRecord.bvid.isBlank() && (resumableRecord.aid == null || resumableRecord.aid <= 0L)) {
            AppToast.show(requireContext(), "本地记录缺少视频标识，无法播放")
            return
        }
        startActivity(
            Intent(requireContext(), PlayerActivity::class.java).apply {
                if (resumableRecord.bvid.isNotBlank()) putExtra(PlayerActivity.EXTRA_BVID, resumableRecord.bvid)
                putExtra(PlayerActivity.EXTRA_CID, resumableRecord.cid)
                resumableRecord.aid?.takeIf { it > 0L }?.let { putExtra(PlayerActivity.EXTRA_AID, it) }
                resumableRecord.epId?.takeIf { it > 0L }?.let { putExtra(PlayerActivity.EXTRA_EP_ID, it) }
                resumableRecord.seasonId?.takeIf { it > 0L }?.let { putExtra(PlayerActivity.EXTRA_SEASON_ID, it) }
                putExtra(PlayerActivity.EXTRA_LOCAL_HISTORY_WORK_ID, resumableRecord.workId)
                putExtra(PlayerActivity.EXTRA_LOCAL_HISTORY_EPISODE_ID, resumableRecord.episodeId)
                putExtra(PlayerActivity.EXTRA_START_POSITION_MS, resumableRecord.progressMs)
            },
        )
    }

    private fun focusFirstItem(): Boolean {
        val b = _binding ?: return false
        pendingFocusFirstItemFromTabSwitch = false
        if (adapter.itemCount == 0) return b.tvStatus.requestFocus()
        b.recycler.post {
            b.recycler.scrollToPosition(0)
            b.recycler.post { adapter.requestFocusOpenAt(0) }
        }
        return true
    }
}
