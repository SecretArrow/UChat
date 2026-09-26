package com.uchat.android.data.repo

import com.uchat.android.core.fs.PathSafety
import com.uchat.android.data.db.ProjectDao
import com.uchat.android.data.db.ProjectEntity
import kotlinx.coroutines.flow.Flow

/** Projects repository (spec #11). Project names are validated with PathSafety. */
class ProjectsRepository(private val dao: ProjectDao) {

    fun observeAll(): Flow<List<ProjectEntity>> = dao.observeAll()

    suspend fun create(name: String, pathInUbuntu: String): Result<ProjectEntity> {
        if (!PathSafety.isSafeName(name)) {
            return Result.failure(IllegalArgumentException("Invalid project name"))
        }
        val entity =
            ProjectEntity(
                name = name,
                pathInUbuntu = pathInUbuntu.ifBlank { "/root/workspace/projects/$name" },
                lastOpenedAt = System.currentTimeMillis(),
            )
        val id = dao.insert(entity)
        return Result.success(entity.copy(id = id))
    }

    suspend fun touch(id: Long) = dao.touch(id, System.currentTimeMillis())

    suspend fun update(project: ProjectEntity) = dao.update(project)

    suspend fun delete(project: ProjectEntity) = dao.delete(project)

    suspend fun byId(id: Long): ProjectEntity? = dao.byId(id)
}
