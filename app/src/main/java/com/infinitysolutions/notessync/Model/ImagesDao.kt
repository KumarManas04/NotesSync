package com.infinitysolutions.notessync.Model

import androidx.room.*

@Dao
interface ImagesDao{
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(imageData: ImageData): Long

    @Query("Select * from images_table where image_id IN (:ids)")
    fun getImagesByIds(ids: List<Long>): List<ImageData>

    @Query("Select image_path from images_table where image_id = :id LIMIT 1")
    fun getImagePathById(id: Long): String

    @Query("Delete from images_table where image_id = :id")
    fun deleteImageById(id: Long)
}