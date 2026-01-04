package com.example.scan.task

import com.example.scan.model.ScannedCode
import com.example.scan.model.Task

sealed class CheckResult {
    object Success : CheckResult()
    data class Failure(val reason: String, val invalidCodes: Set<String> = emptySet()) : CheckResult()
}

interface ITaskProcessor {
    fun check(codes: List<ScannedCode>, task: Task): CheckResult
}
