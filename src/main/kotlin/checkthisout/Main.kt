package checkthisout

import java.io.File
import kotlin.concurrent.thread

fun clone(url: String, targetDir: File) {
    val builder = ProcessBuilder().redirectErrorStream(true).directory(targetDir)
    when {
        url.startsWith("ssh://git@") -> builder.command("git", "clone", "--quiet", url)
        url.startsWith("ssh://hg@")  -> builder.command("hg", "clone", "--quiet", url)
        else -> throw IllegalArgumentException("unsupported url: $url")
    }

    val process = builder.start()
    thread {
        process.inputStream.copyTo(System.out)
    }
    val exitCode = process.waitFor() != 0
    if (exitCode)
        throw Exception("failed to clone $url to $targetDir: $exitCode")
}

fun main(args: Array<String>) {
    if (args.size < 2 || args.size > 4) {
        System.err?.println("usage: checkout-this-out OWNER TARGET-DIR [LOGIN] [PASSWORD]")
        System.exit(1)
    }
    val owner = args[0]
    val targetDir = File(args[1])
    val login = args.getOrNull(2)

    val credentials = login?.let { login -> resolveCredentials(login, args.getOrNull(3)) }

    targetDir.mkdirs()
    for (repo in findRepositories(owner, credentials)) {
        println("${repo.name} - ${repo.sshCloneUrl}")
        clone(repo.sshCloneUrl!!, targetDir)
    }
}

private fun resolveCredentials(login: String, password: String?): Credentials {
    var pass = password
    if (pass == null) {
        val console = System.console()
        checkNotNull(console) { "no console available: give password on command line" }
        pass = String(console.readPassword("password: "))
    }

    return Credentials(login, pass)
}
