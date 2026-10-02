package com.example.capstone.presentation.fragment

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.example.capstone.R
import com.example.capstone.presentation.AssistantViewModel
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton

class AssistantBottomSheetFragment : BottomSheetDialogFragment() {
    private lateinit var viewModel: AssistantViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_assistant, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Adjust style for bottom sheet appearance if needed
        view.setBackgroundResource(R.drawable.bg_white_card)

        viewModel = ViewModelProvider(this)[AssistantViewModel::class.java]

        val scroll = view.findViewById<ScrollView>(R.id.assistantScroll)
        val chatContainer = view.findViewById<LinearLayout>(R.id.assistantChatContainer)
        val promptsContainer = view.findViewById<LinearLayout>(R.id.assistantPromptsContainer)
        val input = view.findViewById<EditText>(R.id.assistantInput)
        val send = view.findViewById<MaterialButton>(R.id.assistantSendButton)
        val suggestion = view.findViewById<TextView>(R.id.assistantSuggestion)
        val statusBadge = view.findViewById<TextView>(R.id.assistantStatusBadge)
        val loading = view.findViewById<ProgressBar>(R.id.assistantLoadingIndicator)

        // Remove 88dp margin in bottom sheet
        val inputCard = view.findViewById<View>(R.id.assistantInputCard)
        val params = inputCard.layoutParams as ViewGroup.MarginLayoutParams
        params.bottomMargin = (16 * resources.displayMetrics.density).toInt()
        inputCard.layoutParams = params

        fun submitMessage() {
            val text = input.text?.toString().orEmpty().trim()
            if (text.isBlank()) {
                Toast.makeText(requireContext(), getString(R.string.assistant_type_question), Toast.LENGTH_SHORT).show()
                return
            }
            viewModel.sendMessage(text)
            input.text?.clear()
        }

        send.setOnClickListener { submitMessage() }

        input.setOnEditorActionListener { _, actionId, event ->
            val enterPressed = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
            if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE || enterPressed) {
                submitMessage()
                true
            } else {
                false
            }
        }

        viewModel.state.observe(viewLifecycleOwner) { state ->
            chatContainer.removeAllViews()
            state.messages.forEach { message ->
                chatContainer.addView(chatBubble(message.text, message.isUser))
            }
            suggestion.text = state.suggestedTopic ?: getString(R.string.assistant_suggestion_default)
            statusBadge.text = state.backendLabel
            loading.visibility = if (state.isLoading) View.VISIBLE else View.GONE
            send.isEnabled = !state.isLoading
            input.isEnabled = !state.isLoading

            promptsContainer.removeAllViews()
            state.prompts.forEach { prompt ->
                val chip = MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonStyle).apply {
                    text = prompt
                    isAllCaps = false
                    setOnClickListener {
                        input.setText(prompt)
                        viewModel.sendMessage(prompt)
                        input.text?.clear()
                    }
                }
                promptsContainer.addView(chip)
            }

            scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        }
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? BottomSheetDialog
        val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.let {
            val behavior = BottomSheetBehavior.from(it)
            val displayMetrics = resources.displayMetrics
            val height = (displayMetrics.heightPixels * 0.7).toInt()
            behavior.peekHeight = height
            it.layoutParams.height = height
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    private fun chatBubble(text: String, isUser: Boolean): View {
        val bubble = TextView(requireContext()).apply {
            this.text = text
            setPadding(20, 16, 20, 16)
            textSize = 14f
            setTextColor(if (isUser) ContextCompat.getColor(context, R.color.text_inverse) else ContextCompat.getColor(context, R.color.text_primary))
            setBackgroundResource(if (isUser) R.drawable.bg_chip_active else R.drawable.bg_white_card)
        }
        val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = 12
            marginStart = if (isUser) 64 else 0
            marginEnd = if (isUser) 0 else 64
        }
        bubble.layoutParams = params
        return bubble
    }
}
