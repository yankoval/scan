package com.example.scan.task

import com.example.scan.model.ScannedCode
import com.example.scan.model.Task

interface ITaskProcessor {
    fun check(codes: List<ScannedCode>, task: Task): Boolean
}
