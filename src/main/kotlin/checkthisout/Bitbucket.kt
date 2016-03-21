package checkthisout

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import org.apache.http.HttpHost
import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.impl.auth.BasicScheme
import org.apache.http.impl.client.BasicAuthCache
import org.apache.http.impl.client.BasicCredentialsProvider
import org.apache.http.impl.client.HttpClients
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME
import java.util.*

fun findRepositories(owner: String, credentials: Credentials?): List<RepositoryInfo> {
    val startUrl = "https://api.bitbucket.org/2.0/repositories/$owner"
    val target = HttpHost("api.bitbucket.org", 443, "https")

    val clientBuilder = HttpClients.custom()
    val httpContext = HttpClientContext()
    if (credentials != null) {
        clientBuilder.setDefaultCredentialsProvider(BasicCredentialsProvider().apply {
            setCredentials(AuthScope.ANY, UsernamePasswordCredentials(credentials.login, credentials.password))
        })

        // Create a local context that forces usage of preemptive basic authentication
        httpContext.authCache = BasicAuthCache().apply {
            put(target, BasicScheme())
        }
    }

    clientBuilder.build().use { client ->
        val repositories = ArrayList<RepositoryInfo>()
        var url: String? = startUrl
        while (url != null) {
            client.execute(HttpGet(url), httpContext).use { response ->
                val result = objectMapper.readValue<RepositoriesResponse>(response.entity.content)
                repositories += result.repositories
                url = result.next
            }
        }
        return repositories
    }
}

data class Credentials(val login: String, val password: String)

data class RepositoriesResponse(
        @JsonProperty("pagelen") val pageLength: Int,
        val size: Int,
        @JsonProperty("values") val repositories: List<RepositoryInfo>,
        val page: Int,
        val next: String?,
        val previous: String? = null) {
}

data class Link(val href: String, val name: String?)

data class RepositoryInfo(
        val scm: String,
        val website: String?,
        @JsonProperty("has_wiki")
        val hasWiki: Boolean,
        val name: String,
        @JsonFormat(with = arrayOf(ACCEPT_SINGLE_VALUE_AS_ARRAY))
        val links: Map<String, List<Link>>,
        @JsonProperty("fork_policy")
        val forkPolicy: String,
        val uuid: String,
        val language: String,
        @JsonProperty("created_on")
        val createdOn: Instant,
        @JsonProperty("full_name")
        val fullName: String,
        @JsonProperty("has_issues")
        val hasIssues: Boolean,
        val owner: Any,
        @JsonProperty("updated_on")
        val updated_on: Instant,
        val size: Int,
        val type: String,
        @JsonProperty("is_private")
        val isPrivate: Boolean,
        val description: String,
        val parent: Any? = null) {

    val cloneLinks: List<Link>
        get() = links["clone"] ?: emptyList()

    val sshCloneUrl: String?
        get() = cloneUrlForProtocol("ssh")

    fun cloneUrlForProtocol(protocol: String): String? =
            cloneLinks.map { it.href }.find { it.startsWith("$protocol:") }
}

/**
 * Jackson ObjectMapper for Bitbucket requests.
 */
private val objectMapper = ObjectMapper().apply {
    registerModule(KotlinModule())
    registerModule(BitbucketModule())
}

/**
 * Jackson module that provides deserializers for types in Bitbucket's API.
 */
private class BitbucketModule : SimpleModule() {
    init {
        addDeserializer(Instant::class.java, object : JsonDeserializer<Instant>() {
            override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Instant? {
                val str = p.text.trim()
                return if (str.any())
                    ZonedDateTime.parse(str, ISO_OFFSET_DATE_TIME).toInstant()
                else
                    null
            }
        })
    }
}
