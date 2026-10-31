package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.data.workspace.WorkspaceGit

const val WORKSPACE_GIT_TOOL_NAME = "workspace_git"

/** 只读动作不需要逐条确认, 改仓库的动作仍然要用户点一下 */
private val GIT_READ_ONLY_ACTIONS = setOf("status", "diff", "numstat", "branches", "worktrees", "log")

internal fun createGitTool(
    targets: List<WorkspaceTarget>,
    workspaceRepository: WorkspaceRepository,
    readOnly: Boolean = false,
): Tool {
    val git = WorkspaceGit(workspaceRepository)
    return Tool(
        name = WORKSPACE_GIT_TOOL_NAME,
        description = """
            Inspect and change git state in the assistant's bound workspace.
            Actions: status (branch, ahead/behind, changed files), diff (unified diff, optional paths), numstat (per-file added/removed),
            log (recent commits), commit (git add -A + commit with the given message), discard (revert the given paths to HEAD, untracked files are deleted),
            branches (list local branches), checkout (switch branch; set create=true for a new branch), worktrees, worktree_add (path + branch), worktree_remove (path).
            Always run status before commit and never commit without a message that describes the actual change.
            ${targets.workspaceNote()}
        """.trimIndent().replace("\n", " "),
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    putWorkspaceProperty(targets)
                    put("action", buildJsonObject {
                        put("type", "string")
                        put(
                            "enum",
                            buildJsonArray {
                                listOf(
                                    "status", "diff", "numstat", "log", "commit", "discard", "discard_hunk",
                                    "branches", "checkout", "worktrees", "worktree_add", "worktree_remove",
                                ).forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
                            },
                        )
                    })
                    put("cwd", buildJsonObject {
                        put("type", "string")
                        put("description", "Working directory relative to the workspace files root. Defaults to the repo root.")
                    })
                    put("paths", buildJsonObject {
                        put("type", "array")
                        put("description", "File paths relative to cwd, for diff / numstat / discard")
                        put("items", buildJsonObject { put("type", "string") })
                    })
                    put("message", buildJsonObject {
                        put("type", "string")
                        put("description", "Commit message, required for commit")
                    })
                    put("branch", buildJsonObject {
                        put("type", "string")
                        put("description", "Branch name for checkout / worktree_add")
                    })
                    put("create", buildJsonObject {
                        put("type", "boolean")
                        put("description", "Create the branch when checking out. Defaults to false.")
                    })
                    put("path", buildJsonObject {
                        put("type", "string")
                        put("description", "File path for discard_hunk, worktree path for worktree_add / worktree_remove")
                    })
                    put("hunk", buildJsonObject {
                        put("type", "integer")
                        put("description", "0-based hunk index for discard_hunk, as returned by diff")
                    })
                },
                required = listOf("action"),
            )
        },
        needsApproval = { params ->
            val obj = params.jsonObject
            val target = obj.resolveWorkspaceOrNull(targets)
            val action = obj.str("action").orEmpty()
            when {
                target == null -> true
                action in GIT_READ_ONLY_ACTIONS && !target.needsApproval(WORKSPACE_GIT_TOOL_NAME) -> false
                action in GIT_READ_ONLY_ACTIONS -> false
                else -> target.needsApproval(WORKSPACE_GIT_TOOL_NAME)
            }
        },
        execute = {
            val params = it.jsonObject
            val target = params.resolveWorkspace(targets)
            val action = params.str("action") ?: error("action is required")
            val cwd = params.str("cwd").orEmpty()
            val paths = (params["paths"] as? JsonArray)
                ?.mapNotNull { element -> element.jsonPrimitive.contentOrNull }
                .orEmpty()

            if (readOnly && action !in GIT_READ_ONLY_ACTIONS) {
                return@Tool listOf(
                    UIMessagePart.Text(
                        buildJsonObject {
                            put(
                                "error",
                                "Only read-only git actions are allowed right now " +
                                    "(${GIT_READ_ONLY_ACTIONS.joinToString()}). Turn plan mode off or raise the " +
                                    "execution permission in the assistant settings to change the repository."
                            )
                            put("action", action)
                        }.toString()
                    )
                )
            }
            val payload = when (action) {
                "status" -> {
                    val status = git.status(target.id, cwd)
                    buildJsonObject {
                        put("branch", status.branch)
                        put("ahead", status.ahead)
                        put("behind", status.behind)
                        put("clean", status.clean)
                        put("files", buildJsonArray {
                            status.files.forEach { file ->
                                add(buildJsonObject {
                                    put("path", file.path)
                                    put("status", file.statusLabel)
                                    put("untracked", file.untracked)
                                })
                            }
                        })
                    }
                }

                "diff" -> {
                    val diff = git.diff(target.id, cwd, paths)
                    buildJsonObject {
                        put("empty", diff.isBlank())
                        put("diff", diff)
                    }
                }

                "numstat" -> buildJsonObject {
                    put("files", buildJsonArray {
                        git.numstat(target.id, cwd, paths).forEach { stat ->
                            add(buildJsonObject {
                                put("path", stat.path)
                                put("added", stat.added)
                                put("removed", stat.removed)
                                put("untracked", stat.untracked)
                            })
                        }
                    })
                }

                "log" -> {
                    val out = git.run(target.id, cwd, "git --no-pager log --oneline -20").output
                    buildJsonObject { put("log", out) }
                }

                "commit" -> {
                    val message = params.str("message")?.trim()
                    require(!message.isNullOrEmpty()) { "message is required for commit" }
                    val result = git.commit(target.id, cwd, message)
                    buildJsonObject {
                        put("ok", result.ok)
                        put("exitCode", result.exitCode)
                        put("output", result.output)
                    }
                }

                "discard" -> {
                    require(paths.isNotEmpty()) { "paths is required for discard" }
                    val result = git.discard(target.id, cwd, paths)
                    buildJsonObject {
                        put("ok", result.ok)
                        put("exitCode", result.exitCode)
                        put("output", result.output)
                    }
                }

                "discard_hunk" -> {
                    val path = params.str("path")?.trim()
                    require(!path.isNullOrEmpty()) { "path is required for discard_hunk" }
                    val hunkIndex = params.str("hunk")?.toIntOrNull() ?: 0
                    val result = git.discardHunk(target.id, cwd, path, hunkIndex)
                    buildJsonObject {
                        put("ok", result.ok)
                        put("exitCode", result.exitCode)
                        put("output", result.output)
                    }
                }

                "branches" -> buildJsonObject {
                    put("branches", buildJsonArray {
                        git.branches(target.id, cwd).forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
                    })
                }

                "checkout" -> {
                    val branch = params.str("branch")?.trim()
                    require(!branch.isNullOrEmpty()) { "branch is required for checkout" }
                    val create = params["create"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
                    val result = git.checkout(target.id, cwd, branch, create)
                    buildJsonObject {
                        put("ok", result.ok)
                        put("exitCode", result.exitCode)
                        put("output", result.output)
                    }
                }

                "worktrees" -> buildJsonObject {
                    put("worktrees", buildJsonArray {
                        git.worktrees(target.id, cwd).forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
                    })
                }

                "worktree_add" -> {
                    val path = params.str("path")?.trim()
                    val branch = params.str("branch")?.trim()
                    require(!path.isNullOrEmpty()) { "path is required for worktree_add" }
                    require(!branch.isNullOrEmpty()) { "branch is required for worktree_add" }
                    val result = git.addWorktree(target.id, cwd, path, branch)
                    buildJsonObject {
                        put("ok", result.ok)
                        put("exitCode", result.exitCode)
                        put("output", result.output)
                    }
                }

                "worktree_remove" -> {
                    val path = params.str("path")?.trim()
                    require(!path.isNullOrEmpty()) { "path is required for worktree_remove" }
                    val result = git.removeWorktree(target.id, cwd, path)
                    buildJsonObject {
                        put("ok", result.ok)
                        put("exitCode", result.exitCode)
                        put("output", result.output)
                    }
                }

                else -> error("Unknown action: $action")
            }
            listOf(UIMessagePart.Text(text = payload.toString()))
        },
    )
}

private fun JsonObject.str(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull
