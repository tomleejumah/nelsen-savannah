package com.app.nisisiafrica.Fragment

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.app.nisisiafrica.R

class LiquidPagerFragment : Fragment() {
    @kotlin.jvm.JvmField
    var termsView: View? = null
    private var position: Int = -1
    private var callback: OnTermsAndConditionsListener? = null
//    private var termsView: View? = null

    interface OnTermsAndConditionsListener {
        fun onTermsAndConditionsShown(isShown: Boolean)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        callback = context as? OnTermsAndConditionsListener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        position = arguments?.getInt("POSITION") ?: 1
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val layoutId = when (position) {
            1 -> R.layout.first_on_boarding_page
            2 -> R.layout.second_on_boarding_page
            3 -> R.layout.third_on_boarding_page
            else -> R.layout.terms_and_conditions
        }
        val view = inflater.inflate(layoutId, container, false)
        if (layoutId == R.layout.terms_and_conditions) {
            termsView = view // Save the reference to the termsView
        }
        return inflater.inflate(layoutId, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val color = when (position) {
            1 -> Color.parseColor("#4285F4") // Google Blue
            2 -> Color.parseColor("#EA4335") // Google Red
            3 -> Color.parseColor("#FBBC05") // Google Yellow
            else -> Color.WHITE
        }
        view.setBackgroundColor(color)
    }

    override fun onDetach() {
        super.onDetach()
        callback = null
    }

    companion object {
        fun newInstance(position: Int) = LiquidPagerFragment().apply {
            arguments = Bundle().apply {
                putInt("POSITION", position)
            }
        }
    }
}