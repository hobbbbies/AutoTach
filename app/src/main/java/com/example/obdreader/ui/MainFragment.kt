package com.example.obdreader.ui

import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.obdreader.R
import com.example.obdreader.viewmodel.ObdViewModel

private const val TAG="MainFragment"
class MainFragment : Fragment(R.layout.fragment_main) {
    private val viewModel: ObdViewModel by activityViewModels()


}