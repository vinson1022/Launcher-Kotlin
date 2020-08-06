/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.folder

import com.android.launcher3.FolderInfo
import com.android.launcher3.InvariantDeviceProfile

/**
 * Verifies whether an item in a Folder is displayed in the FolderIcon preview.
 */
class FolderIconPreviewVerifier(profile: InvariantDeviceProfile) {
    private val maxGridCountX = profile.numFolderColumns
    private val maxGridCountY = profile.numFolderRows
    private val maxItemsPerPage = maxGridCountX * maxGridCountY
    private val gridSize = IntArray(2)
    private var gridCountX = 0
    private var displayingUpperLeftQuadrant = false

    fun setFolderInfo(info: FolderInfo) {
        val numItemsInFolder = info.contents.size
        FolderPagedView.calculateGridSize(numItemsInFolder, 0, 0, maxGridCountX,
                maxGridCountY, maxItemsPerPage, gridSize)
        gridCountX = gridSize[0]
        displayingUpperLeftQuadrant = numItemsInFolder > ClippedFolderIconLayoutRule.MAX_NUM_ITEMS_IN_PREVIEW
    }

    /**
     * @param page The page the item is on.
     * @param rank The rank of the item.
     * @return True iff the icon is in the 2x2 upper left quadrant of the Folder.
     */
    fun isItemInPreview(page: Int = 0, rank: Int): Boolean {
        // First page items are laid out such that the first 4 items are always in the upper
        // left quadrant. For all other pages, we need to check the row and col.
        if (page > 0 || displayingUpperLeftQuadrant) {
            val col = rank % gridCountX
            val row = rank / gridCountX
            return col < 2 && row < 2
        }
        return rank < ClippedFolderIconLayoutRule.MAX_NUM_ITEMS_IN_PREVIEW
    }
}