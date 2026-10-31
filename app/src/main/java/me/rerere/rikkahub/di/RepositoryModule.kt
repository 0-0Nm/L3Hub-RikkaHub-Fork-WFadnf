package me.rerere.rikkahub.di

import android.content.Context
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.chat.DraftStore
import me.rerere.rikkahub.data.files.FileFolders
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.FavoriteRepository
import me.rerere.rikkahub.data.repository.FilesRepository
import me.rerere.rikkahub.data.repository.FolderRepository
import me.rerere.rikkahub.data.repository.GenMediaRepository
import me.rerere.rikkahub.data.repository.MediaCreationRepository
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.data.repository.TranslationHistoryRepository
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.data.workspace.WorkspaceSnapshotStore
import me.rerere.rikkahub.data.skills.SkillManager
import me.rerere.workspace.ProotShellRunner
import me.rerere.workspace.RootfsInstaller
import me.rerere.workspace.WorkspaceBindMount
import me.rerere.workspace.WorkspaceManager
import me.rerere.workspace.ssh.SshConnectionPool
import org.koin.dsl.module
import org.koin.dsl.onClose
import java.io.File

val repositoryModule = module {
    single {
        ConversationRepository(get(), get(), get(), get(), get(), get())
    }

    single {
        FolderRepository(get(), get())
    }

    single {
        MemoryRepository(get())
    }

    single {
        TranslationHistoryRepository(get())
    }

    single {
        GenMediaRepository(get())
    }

    single {
        MediaCreationRepository(context = get(), database = get(), dao = get(), json = get())
    }

    single {
        FilesRepository(get())
    }

    single {
        FavoriteRepository(get())
    }

    single {
        val context: Context = get()
        WorkspaceManager(
            baseDir = File(context.filesDir, "workspaces"),
            shellRunner = ProotShellRunner(
                nativeLibraryDir = File(context.applicationInfo.nativeLibraryDir),
            ),
            // 同一份挂载表同时用于 PRoot 的 -b 参数 (命令执行与终端)、文件工具的路径解析和写入审批, 避免多处漂移
            bindMounts = listOf(
                WorkspaceBindMount(
                    source = File(context.filesDir, FileFolders.SKILLS).apply { mkdirs() },
                    target = "/skills",
                    writable = true,
                ),
                WorkspaceBindMount(
                    source = File(context.filesDir, FileFolders.BUILTIN_SKILLS).apply { mkdirs() },
                    target = "/builtin_skills",
                ),
                WorkspaceBindMount(
                    source = File(context.filesDir, FileFolders.TOOL_OUTPUTS).apply { mkdirs() },
                    target = "/tool_outputs",
                ),
                WorkspaceBindMount(
                    source = File(context.filesDir, FileFolders.UPLOAD).apply { mkdirs() },
                    target = "/upload",
                ),
            ),
        )
    }

    single {
        RootfsInstaller(get())
    }

    single {
        SshConnectionPool()
    } onClose { it?.close() }

    single {
        WorkspaceRepository(get(), get(), get(), get(), get())
    }

    // AI 改文件前的原内容快照，用来撤销某一轮的修改
    single {
        val context: Context = get()
        WorkspaceSnapshotStore(context, get(), get())
    }

    // 流式生成的草稿：独立文件，不进数据库；进程被杀后打开会话时从这里补回写到一半的内容
    single {
        val context: Context = get()
        DraftStore(
            root = File(context.filesDir, "drafts"),
            scope = get<AppScope>(),
        )
    }

    single {
        FilesManager(get(), get(), get())
    }

    single {
        SkillManager(get(), get())
    }
}
