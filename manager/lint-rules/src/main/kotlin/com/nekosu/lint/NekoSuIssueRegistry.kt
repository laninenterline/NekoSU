package com.nekosu.lint

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.detector.api.CURRENT_API

class NekoSuIssueRegistry : IssueRegistry() {
    override val issues = listOf(
        SegmentedColumnScopeConditionDetector.ISSUE,
        DirectHorizontalPagerDetector.ISSUE,
    )

    override val api = CURRENT_API
}
