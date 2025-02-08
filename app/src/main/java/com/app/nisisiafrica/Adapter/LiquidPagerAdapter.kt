package com.app.nisisiafrica.Adapter;

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import com.app.nisisiafrica.Fragment.LiquidPagerFragment


class LiquidPagerAdapter(fm: FragmentManager?) :
    FragmentPagerAdapter(fm!!, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
    override fun getItem(position: Int): Fragment {
        return LiquidPagerFragment.newInstance(position + 1)
    }

    override fun getCount(): Int {
        return 4 // 3 onboarding pages + 1 terms page
    }
}