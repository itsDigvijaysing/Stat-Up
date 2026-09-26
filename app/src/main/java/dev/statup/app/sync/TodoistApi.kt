package dev.statup.app.sync

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

class TodoistAuthException(message: String) : Exception(message)

class TodoistApi(private val httpClient: HttpClient) {

    companion object {
        private const val BASE_URL = "https://api.todoist.com/api/v1"
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun getTasks(token: String): Result<List<TodoistTask>> {
        return try {
            val response = httpClient.submitForm(
                url = "$BASE_URL/sync",
                formParameters = parameters {
                    append("sync_token", "*")
                    append("resource_types", "[\"items\"]")
                }
            ) {
                header(HttpHeaders.Authorization, "Bearer $token")
            }

            if (response.status == HttpStatusCode.Unauthorized || response.status == HttpStatusCode.Forbidden) {
                return Result.failure(TodoistAuthException("Invalid or expired Todoist token (HTTP ${response.status.value})"))
            }

            val syncResponse: SyncResponse = response.body()
            val tasks = syncResponse.items.filter { !it.isCompleted }
            Result.success(tasks)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * `annotate_items=true` returns each entry with an `item_object` containing priority & labels.
     * Parsed manually (not a fixed data class) because Todoist sometimes nests results differently.
     */
    suspend fun getCompletedTasks(token: String, limit: Int = 30): Result<List<CompletedTask>> {
        return try {
            val response = httpClient.get("$BASE_URL/tasks/completed") {
                header(HttpHeaders.Authorization, "Bearer $token")
                parameter("limit", limit.toString())
                parameter("annotate_items", "true")
            }

            if (response.status == HttpStatusCode.Unauthorized || response.status == HttpStatusCode.Forbidden) {
                return Result.failure(TodoistAuthException("Invalid or expired Todoist token (HTTP ${response.status.value})"))
            }

            val responseText: String = response.bodyAsText()
            val jsonElement = json.parseToJsonElement(responseText)

            if (jsonElement !is JsonObject || "items" !in jsonElement) {
                val keys = if (jsonElement is JsonObject) jsonElement.keys.joinToString() else "not-object"
                return Result.failure(Exception("Unexpected response [$keys]: ${responseText.take(150)}"))
            }

            val itemsArray = jsonElement["items"] as? JsonArray ?: return Result.success(emptyList())

            // Parse each item, extracting priority & labels from item_object
            val tasks = itemsArray.mapNotNull { element ->
                try {
                    val obj = element.jsonObject
                    // contentOrNull, not content: JsonNull.content is the STRING "null", which
                    // would claim the unique externalId index and silently drop later null-id tasks.
                    val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: ""
                    val taskId = obj["task_id"]?.jsonPrimitive?.contentOrNull ?: ""
                    val content = obj["content"]?.jsonPrimitive?.contentOrNull ?: ""
                    val completedAt = obj["completed_at"]?.jsonPrimitive?.contentOrNull

                    // Extract priority & labels from item_object (present with annotate_items=true)
                    val itemObj = obj["item_object"]?.jsonObject
                    val priority = itemObj?.get("priority")?.jsonPrimitive?.int ?: 1
                    val labels = itemObj?.get("labels")?.jsonArray
                        ?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()

                    CompletedTask(
                        id = id,
                        taskId = taskId,
                        content = content,
                        priority = priority,
                        labels = labels,
                        completedAt = completedAt
                    )
                } catch (e: Exception) {
                    null // skip malformed items
                }
            }

            Result.success(tasks)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun testConnection(token: String): Result<Boolean> {
        return try {
            val response = httpClient.submitForm(
                url = "$BASE_URL/sync",
                formParameters = parameters {
                    append("sync_token", "*")
                    append("resource_types", "[\"user\"]")
                }
            ) {
                header(HttpHeaders.Authorization, "Bearer $token")
            }

            if (response.status.isSuccess()) {
                Result.success(true)
            } else {
                Result.failure(Exception("HTTP ${response.status.value}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

// --- Sync API response (for active tasks) ---

@Serializable
data class SyncResponse(
    @SerialName("sync_token")
    val syncToken: String? = null,
    val items: List<TodoistTask> = emptyList()
)

@Serializable
data class TodoistTask(
    val id: String,
    val content: String,
    val description: String? = null,
    val priority: Int = 1,
    @SerialName("project_id")
    val projectId: String? = null,
    val labels: List<String> = emptyList(),
    val due: TodoistDue? = null,
    @SerialName("checked")
    val isCompleted: Boolean = false
)

@Serializable
data class TodoistDue(
    val date: String? = null,
    val datetime: String? = null,
    val string: String? = null
)

// --- Completed tasks response (v1 API) ---

@Serializable
data class CompletedTask(
    val id: String = "",
    @SerialName("task_id")
    val taskId: String = "",
    @SerialName("item_id")
    val itemId: String = "",
    val content: String = "",
    val priority: Int = 1,
    val labels: List<String> = emptyList(),
    @SerialName("project_id")
    val projectId: String? = null,
    @SerialName("completed_at")
    val completedAt: String? = null,
    @SerialName("completed_date")
    val completedDate: String? = null
) {
    /** Stable identifier - prefer id, then task_id, then item_id */
    val stableId: String get() = id.ifBlank { taskId.ifBlank { itemId } }
}
