package com.example.capstone.presentation.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.capstone.R
import com.example.capstone.data.MeshMessage
import com.example.capstone.data.MeshMessageType
import com.example.capstone.data.SafeReadyPreferences
import com.example.capstone.presentation.adapter.MeshMessageAdapter
import com.example.capstone.presentation.viewmodel.MeshViewModel
import kotlinx.coroutines.flow.collect
import java.util.UUID

class MeshChatFragment : Fragment() {
    private lateinit var meshViewModel: MeshViewModel
    private lateinit var adapter: MeshMessageAdapter
    private var targetDeviceId: String? = null
    private var targetDeviceName: String? = null

    companion object {
        private const val ARG_DEVICE_ID = "arg_device_id"
        private const val ARG_DEVICE_NAME = "arg_device_name"

        fun newInstance(deviceId: String, deviceName: String?): MeshChatFragment {
            val f = MeshChatFragment()
            val args = Bundle()
            args.putString(ARG_DEVICE_ID, deviceId)
            args.putString(ARG_DEVICE_NAME, deviceName)
            f.arguments = args
            return f
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_mesh_chat, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        meshViewModel = ViewModelProvider(requireActivity())[MeshViewModel::class.java]
        adapter = MeshMessageAdapter()

        targetDeviceId = arguments?.getString(ARG_DEVICE_ID)
        targetDeviceName = arguments?.getString(ARG_DEVICE_NAME)

        val rv = view.findViewById<RecyclerView>(R.id.rvChatMessages)
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        val input = view.findViewById<EditText>(R.id.etChatInput)
        val send = view.findViewById<Button>(R.id.btnSendChat)

        send.setOnClickListener {
            val text = input.text.toString().trim()
            if (text.isNotEmpty()) {
                val prefs = SafeReadyPreferences(requireContext())
                val msg = MeshMessage(
                    id = UUID.randomUUID().toString(),
                    senderId = "",
                    senderName = prefs.getUserProfile().name,
                    type = MeshMessageType.INFO,
                    content = text
                )
                meshViewModel.broadcast(msg)
                input.setText("")
            }
        }

        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            meshViewModel.messages.collect { list ->
                adapter.submitList(list)
                rv.scrollToPosition(adapter.itemCount - 1)
            }
        }
    }
}