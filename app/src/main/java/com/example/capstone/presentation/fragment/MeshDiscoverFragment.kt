package com.example.capstone.presentation.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.capstone.R
import com.example.capstone.presentation.adapter.MeshDeviceAdapter
import com.example.capstone.presentation.viewmodel.MeshViewModel
import kotlinx.coroutines.flow.collect

class MeshDiscoverFragment : Fragment() {
    private lateinit var meshViewModel: MeshViewModel
    private lateinit var adapter: MeshDeviceAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_mesh_discover, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        meshViewModel = ViewModelProvider(requireActivity())[MeshViewModel::class.java]

        adapter = MeshDeviceAdapter { device ->
            parentFragmentManager.beginTransaction()
                .replace(R.id.mainFragmentContainer, MeshChatFragment.newInstance(device.deviceId, device.deviceName))
                .addToBackStack(null)
                .commit()
        }

        val rv = view.findViewById<RecyclerView>(R.id.rvMeshDevices)
        rv.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        rv.adapter = adapter

        view.findViewById<Button>(R.id.btnAdvertiseOnly).setOnClickListener {
            meshViewModel.startAdvertisingOnly()
        }

        view.findViewById<Button>(R.id.btnDiscoverOnly).setOnClickListener {
            meshViewModel.startDiscoveryOnly()
        }

        view.findViewById<Button>(R.id.btnRefreshMesh).setOnClickListener {
            meshViewModel.restartMesh()
        }

        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            meshViewModel.nearbyDevices.collect { list ->
                adapter.submitList(list)
                view.findViewById<TextView>(R.id.tvMeshDiscoverStatus).text = "Found ${list.size} nearby device(s)"
            }
        }

        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            meshViewModel.connectionState.collect { state ->
                if (!state.lastError.isNullOrBlank()) {
                    view.findViewById<TextView>(R.id.tvMeshDiscoverStatus).text = "Error: ${state.lastError}"
                }
            }
        }
    }
}