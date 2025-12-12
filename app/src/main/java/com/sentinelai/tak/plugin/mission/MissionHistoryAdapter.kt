package com.sentinelai.tak.plugin.mission

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.appcompat.widget.AppCompatTextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sentinelai.tak.plugin.R
import com.sentinelai.tak.plugin.databinding.ItemMissionHistoryBinding
import com.sentinelai.tak.plugin.network.dto.MissionAnalysisResponseDto
import java.time.format.DateTimeFormatter

class MissionHistoryAdapter :
    ListAdapter<MissionHistoryItem, MissionHistoryAdapter.MissionHistoryViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MissionHistoryViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemMissionHistoryBinding.inflate(inflater, parent, false)
        return MissionHistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MissionHistoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class MissionHistoryViewHolder(private val binding: ItemMissionHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private val timestampFormatter: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z")

        fun bind(item: MissionHistoryItem) {
            val context = binding.root.context
            binding.questionText.text = item.question
            binding.timestampText.text = timestampFormatter.format(item.timestamp)

            when {
                item.error != null -> {
                    binding.statusText.text = context.getString(R.string.mission_analysis_status_failed)
                    binding.errorText.text = item.error
                    binding.errorText.isVisible = true
                }

                item.response == null -> {
                    binding.statusText.text = context.getString(R.string.mission_analysis_analyzing)
                    binding.errorText.text = ""
                    binding.errorText.isVisible = false
                }

                else -> {
                    binding.statusText.text = context.getString(R.string.mission_analysis_status_completed)
                    binding.errorText.text = ""
                    binding.errorText.isVisible = false
                }
            }

            if (item.response != null) {
                bindResponse(item.response)
            } else {
                clearResponse()
            }
        }

        private fun bindResponse(response: MissionAnalysisResponseDto) {
            binding.intentText.isVisible = true
            binding.intentText.text = binding.root.context.getString(
                R.string.mission_analysis_intent_label,
                response.intent,
            )

            binding.summaryText.isVisible = true
            binding.summaryText.text = response.summary

            if (response.risks.isNotEmpty()) {
                val risksText = response.risks.joinToString("\n") { "• ${it}" }
                binding.risksText.isVisible = true
                binding.risksText.text = binding.root.context.getString(
                    R.string.mission_analysis_risks_label,
                ) + "\n" + risksText
            } else {
                hideAndClear(binding.risksText)
            }

            if (response.recommendations.isNotEmpty()) {
                val recommendationsText = response.recommendations.joinToString("\n") { "• ${it}" }
                binding.recommendationsText.isVisible = true
                binding.recommendationsText.text = binding.root.context.getString(
                    R.string.mission_analysis_recommendations_label,
                ) + "\n" + recommendationsText
            } else {
                hideAndClear(binding.recommendationsText)
            }
        }

        private fun clearResponse() {
            hideAndClear(binding.intentText)
            hideAndClear(binding.summaryText)
            hideAndClear(binding.risksText)
            hideAndClear(binding.recommendationsText)
        }

        private fun hideAndClear(view: View) {
            view.isVisible = false
            if (view is AppCompatTextView) {
                view.text = ""
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<MissionHistoryItem>() {
        override fun areItemsTheSame(oldItem: MissionHistoryItem, newItem: MissionHistoryItem): Boolean {
            return oldItem.timestamp == newItem.timestamp && oldItem.question == newItem.question
        }

        override fun areContentsTheSame(oldItem: MissionHistoryItem, newItem: MissionHistoryItem): Boolean {
            return oldItem == newItem
        }
    }
}
