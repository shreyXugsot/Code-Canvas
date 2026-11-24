package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY lastOpenedAt DESC, updatedAt DESC")
    fun observeAllProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :projectId")
    fun observeProjectById(projectId: Long): Flow<ProjectEntity?>

    @Query("SELECT * FROM projects WHERE id = :projectId")
    suspend fun getProjectById(projectId: Long): ProjectEntity?

    @Query("SELECT name FROM projects")
    suspend fun getAllProjectNames(): List<String>

    @Query("SELECT COUNT(*) FROM projects")
    suspend fun getProjectCount(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProject(project: ProjectEntity): Long

    @Update
    suspend fun updateProject(project: ProjectEntity)

    @Query("UPDATE projects SET lastOpenedAt = :timestamp WHERE id = :projectId")
    suspend fun touchLastOpened(projectId: Long, timestamp: Long)

    @Query("UPDATE projects SET updatedAt = :timestamp WHERE id = :projectId")
    suspend fun touchUpdated(projectId: Long, timestamp: Long)

    @Query("DELETE FROM projects WHERE id = :projectId")
    suspend fun deleteProjectById(projectId: Long)
}

@Dao
interface NodeDao {
    @Query("SELECT * FROM nodes")
    fun observeAllNodes(): Flow<List<NodeEntity>>

    @Query("SELECT * FROM nodes WHERE projectId = :projectId ORDER BY isFolder DESC, name COLLATE NOCASE ASC")
    fun observeNodesByProject(projectId: Long): Flow<List<NodeEntity>>

    @Query("SELECT * FROM nodes WHERE isFavorite = 1 AND isFolder = 0 ORDER BY updatedAt DESC")
    fun observeFavoriteFiles(): Flow<List<NodeEntity>>

    @Query("SELECT * FROM nodes WHERE id = :nodeId")
    fun observeNodeById(nodeId: Long): Flow<NodeEntity?>

    @Query("SELECT * FROM nodes WHERE id = :nodeId")
    suspend fun getNodeById(nodeId: Long): NodeEntity?

    @Query("SELECT * FROM nodes WHERE projectId = :projectId")
    suspend fun getNodesForProject(projectId: Long): List<NodeEntity>

    @Query("SELECT name FROM nodes WHERE projectId = :projectId AND ((:parentId IS NULL AND parentId IS NULL) OR parentId = :parentId)")
    suspend fun getSiblingNames(projectId: Long, parentId: Long?): List<String>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertNode(node: NodeEntity): Long

    @Update
    suspend fun updateNode(node: NodeEntity)

    @Query("UPDATE nodes SET isFavorite = :isFavorite WHERE id = :nodeId")
    suspend fun setFavorite(nodeId: Long, isFavorite: Boolean)

    @Query("DELETE FROM nodes WHERE id IN (:ids)")
    suspend fun deleteNodesByIds(ids: List<Long>)
}

@Dao
interface LibraryDao {
    @Query("SELECT * FROM library_items ORDER BY updatedAt DESC")
    fun observeAllItems(): Flow<List<LibraryItemEntity>>

    @Query("SELECT * FROM library_items WHERE id = :id")
    suspend fun getItemById(id: Long): LibraryItemEntity?

    @Query("SELECT COUNT(*) FROM library_items")
    suspend fun getCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: LibraryItemEntity): Long

    @Update
    suspend fun updateItem(item: LibraryItemEntity)

    @Query("DELETE FROM library_items WHERE id = :id")
    suspend fun deleteItemById(id: Long)
}
