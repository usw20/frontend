package com.cookandroid.phantom

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment

class SecurityKnowledgeFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // 디자인 개선이 완료된 XML 레이아웃을 불러옵니다.
        return inflater.inflate(R.layout.fragment_security_knowledge, container, false)
    }
}