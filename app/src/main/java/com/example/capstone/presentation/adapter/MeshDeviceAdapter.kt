package com.example.capstone.presentation.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.capstone.R
import com.example.capstone.data.MeshDevice
import java.util.Locale

class MeshDeviceAdapter(private val onItemClick: ((MeshDevice) -> Unit)? = null) : ListAdapter<MeshDevice, MeshDeviceAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_mesh_device, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
        holder.itemView.setOnClickListener { onItemClick?.invoke(item) }
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvName: TextView = view.findViewById(R.id.tvDeviceName)
        private val tvStatus: TextView = view.findViewById(R.id.tvDeviceStatus)
        private val ivSignal: ImageView = view.findViewById(R.id.ivSignalStrength)

        fun bind(device: MeshDevice) {
            tvName.text = device.deviceName
            
            val distanceStr = if (device.estimatedDistanceMeters != null) {
                String.format(Locale.getDefault(), "%.1fm away", device.estimatedDistanceMeters)
            } else {
                "Near you"
            }
            
            val status = if (device.isActive) "Connected" else "Last seen: Just now"
            tvStatus.text = itemView.context.getString(R.string.status_connected_format, status, distanceStr)

            // Simple signal mapping using existing ic_alert_circle as fallback if specific icons missing
            // Ideally should have dedicated signal icons, but ic_alert_circle is in the project.
            ivSignal.setImageResource(R.drawable.ic_alert_circle)
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<MeshDevice>() {
        override fun areItemsTheSame(oldItem: MeshDevice, newItem: MeshDevice): Boolean =
            oldItem.deviceId == newItem.deviceId

        override fun areContentsTheSame(oldItem: MeshDevice, newItem: MeshDevice): Boolean =
            oldItem == newItem
    }
}
