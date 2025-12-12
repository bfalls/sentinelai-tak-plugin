package com.sentinelai.tak.plugin.mission

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.sentinelai.tak.plugin.R
import com.sentinelai.tak.plugin.config.SentinelConfigRepository
import com.sentinelai.tak.plugin.databinding.FragmentMissionAnalysisBinding
import com.sentinelai.tak.plugin.network.SentinelApiClient
import com.sentinelai.tak.plugin.network.SentinelApiException
import com.sentinelai.tak.plugin.network.dto.LocationDto
import com.sentinelai.tak.plugin.network.dto.MissionAnalysisRequestDto
import com.sentinelai.tak.plugin.network.dto.MissionAnalysisResponseDto
import com.sentinelai.tak.plugin.network.dto.SignalDto
import com.sentinelai.tak.plugin.network.dto.TimeWindowDto
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

class MissionAnalysisFragment : Fragment() {

    private var _binding: FragmentMissionAnalysisBinding? = null
    private val binding get() = _binding!!

    private val isoFormatter: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
    private val displayFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z")

    private var startTime: ZonedDateTime = ZonedDateTime.now(ZoneOffset.UTC).minusHours(1)
    private var endTime: ZonedDateTime = ZonedDateTime.now(ZoneOffset.UTC)

    private val historyAdapter = MissionHistoryAdapter()
    private var historyItems: MutableList<MissionHistoryItem> = mutableListOf()

    private val configRepository by lazy { SentinelConfigRepository(requireContext()) }
    private val apiClient by lazy { SentinelApiClient(configRepository) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentMissionAnalysisBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.historyList.adapter = historyAdapter

        binding.startTimeValue.setOnClickListener { selectDateTime(isStart = true) }
        binding.endTimeValue.setOnClickListener { selectDateTime(isStart = false) }
        binding.analyzeButton.setOnClickListener { submitAnalysis() }

        updateTimeLabels()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun selectDateTime(isStart: Boolean) {
        val current = if (isStart) startTime else endTime
        val context = requireContext()

        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val pickedDate = current.withYear(year).withMonth(month + 1).withDayOfMonth(dayOfMonth)
                TimePickerDialog(
                    context,
                    { _, hourOfDay, minute ->
                        val pickedDateTime = pickedDate.withHour(hourOfDay).withMinute(minute)
                        if (isStart) {
                            startTime = pickedDateTime
                        } else {
                            endTime = pickedDateTime
                        }
                        ensureTimeWindowOrder()
                        updateTimeLabels()
                    },
                    current.hour,
                    current.minute,
                    true,
                ).show()
            },
            current.year,
            current.monthValue - 1,
            current.dayOfMonth,
        ).show()
    }

    private fun submitAnalysis() {
        val question = binding.questionInput.text?.toString()?.trim().orEmpty()
        if (question.isBlank()) {
            binding.questionInputLayout.error = getString(R.string.mission_analysis_error_missing_question)
            binding.errorMessage.isVisible = false
            return
        }

        binding.questionInputLayout.error = null
        binding.errorMessage.isVisible = false

        val pendingItem = MissionHistoryItem(
            question = question,
            timestamp = ZonedDateTime.now(ZoneOffset.UTC),
            response = null,
            error = null,
        )
        historyItems = (historyItems + pendingItem).toMutableList()
        historyAdapter.submitList(historyItems.toList())

        setLoading(true)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val request = buildMissionAnalysisRequest(question)
                val response = apiClient.analyzeMission(request)
                updateHistoryItem(pendingItem, response = response)
            } catch (ex: Exception) {
                val errorMessage = when (ex) {
                    is SentinelApiException -> ex.message ?: getString(R.string.mission_analysis_status_failed)
                    else -> ex.localizedMessage ?: getString(R.string.mission_analysis_status_failed)
                }
                binding.errorMessage.text = errorMessage
                binding.errorMessage.isVisible = true
                updateHistoryItem(pendingItem, error = errorMessage)
            } finally {
                setLoading(false)
            }
        }
    }

    private fun updateHistoryItem(
        original: MissionHistoryItem,
        response: MissionAnalysisResponseDto? = null,
        error: String? = null,
    ) {
        val updatedList = historyItems.map { item ->
            if (item === original) {
                item.copy(response = response, error = error)
            } else {
                item
            }
        }
        historyItems = updatedList.toMutableList()
        historyAdapter.submitList(updatedList)
    }

    private fun buildMissionAnalysisRequest(question: String): MissionAnalysisRequestDto {
        val includeNotes = binding.includeMissionNotesToggle.isChecked
        val includeMapExtent = binding.includeMapExtentToggle.isChecked
        ensureTimeWindowOrder()
        updateTimeLabels()

        return MissionAnalysisRequestDto(
            missionId = null,
            missionMetadata = mapOf(
                "include_markers" to binding.includeMarkersToggle.isChecked,
                "include_map_extent" to includeMapExtent,
                "include_mission_notes" to includeNotes,
                "question" to question,
            ),
            signals = listOf(
                SignalDto(
                    type = "USER_QUERY",
                    description = question,
                    timestamp = isoFormatter.format(ZonedDateTime.now(ZoneOffset.UTC)),
                    metadata = mapOf("source" to "mission_analysis_panel"),
                ),
            ),
            notes = if (includeNotes) getString(R.string.mission_analysis_placeholder_notes) else null,
            location = if (includeMapExtent) {
                LocationDto(
                    latitude = 0.0,
                    longitude = 0.0,
                    description = getString(R.string.mission_analysis_placeholder_location),
                )
            } else {
                null
            },
            timeWindow = TimeWindowDto(
                start = isoFormatter.format(startTime),
                end = isoFormatter.format(endTime),
            ),
            intent = null,
        )
    }

    private fun ensureTimeWindowOrder() {
        if (startTime.isAfter(endTime)) {
            endTime = startTime.plusHours(1)
        }
    }

    private fun setLoading(isLoading: Boolean) {
        binding.loadingIndicator.isVisible = isLoading
        binding.analyzeButton.isEnabled = !isLoading
    }

    private fun updateTimeLabels() {
        binding.startTimeValue.text = displayFormatter.format(startTime)
        binding.endTimeValue.text = displayFormatter.format(endTime)
    }

    companion object {
        fun newInstance(): MissionAnalysisFragment = MissionAnalysisFragment()
    }
}
