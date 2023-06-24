package org.helllabs.android.xmp.service.utils

import java.util.Collections

class QueueManager(
    fileList: MutableList<String>,
    private var start: Int,
    shuffle: Boolean,
    loop: Boolean,
    keepFirst: Boolean
) {
    private val list: MutableList<String>
    private val ridx: RandomIndex
    var index: Int
    private val shuffleMode: Boolean
    private val loopListMode: Boolean
    private var randomStart = 0

    init {
        if (start >= fileList.size) {
            start = fileList.size - 1
        }
        if (keepFirst) {
            Collections.swap(fileList, 0, start)
            start = 0
            randomStart = 1
        }
        index = start
        list = fileList
        ridx = RandomIndex(randomStart, fileList.size)
        shuffleMode = shuffle
        loopListMode = loop
    }

    fun add(fileList: List<String>) {
        if (fileList.isNotEmpty()) {
            ridx.extend(fileList.size, index + 1)
            list.addAll(fileList)
        }
    }

    fun size(): Int {
        return list.size
    }

    operator fun next(): Boolean {
        index++
        if (index >= list.size) {
            index = if (loopListMode) {
                ridx.randomize()
                0
            } else {
                return false
            }
        }
        return true
    }

    fun previous() {
        index -= 2
        if (index < -1) {
            if (loopListMode) {
                index += list.size
            } else {
                index = -1
            }
        }
    }

    fun restart() {
        index = -1
    }

    val filename: String
        get() {
            val idx = if (shuffleMode) ridx.getIndex(index) else index
            return list[idx]
        }
}
