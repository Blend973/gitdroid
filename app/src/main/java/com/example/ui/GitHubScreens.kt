package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.api.GitHubCommit
import com.example.data.api.GitHubContent
import com.example.data.api.GitHubIssue
import com.example.data.api.GitHubApiRepository
import com.example.data.api.GitHubUser
import com.example.data.api.GitHubRelease
import com.example.data.api.GitHubReleaseAsset
import com.example.data.api.RetrofitClient
import com.example.data.local.BookmarkedRepoEntity
import com.example.data.local.RecentSearchEntity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll

import androidx.compose.foundation.shape.CircleShape
import androidx.activity.compose.BackHandler
import com.example.data.api.GitHubCommitDetail
import com.example.data.api.GitHubCommitFile
import android.annotation.SuppressLint
import android.util.Base64

val LocalGitHubViewModel = androidx.compose.runtime.staticCompositionLocalOf<GitHubViewModel?> { null }

fun handleGitHubUrl(url: String, viewModel: GitHubViewModel): Boolean {
    var cleanUrl = url.trim()
    val lowerUrl = cleanUrl.lowercase()
    if (!lowerUrl.startsWith("http://") && !lowerUrl.startsWith("https://")) {
        if (lowerUrl.startsWith("github.com") || lowerUrl.startsWith("www.github.com")) {
            cleanUrl = "https://" + cleanUrl
        } else if (lowerUrl.startsWith("raw.githubusercontent.com")) {
            cleanUrl = "https://" + cleanUrl
        } else {
            return false
        }
    }
    
    val uri = try {
        android.net.Uri.parse(cleanUrl)
    } catch (e: Exception) {
        return false
    }
    
    val host = uri.host ?: return false
    val isRawGithub = host.contains("raw.githubusercontent.com")
    if (!host.contains("github.com") && !isRawGithub) {
        return false
    }
    
    // Convert path segments safely
    val pathSegments = uri.pathSegments ?: return false
    
    if (isRawGithub) {
        if (pathSegments.isEmpty()) {
            viewModel.navigateTo(Screen.Dashboard)
            return true
        }
        val owner = pathSegments[0]
        if (pathSegments.size == 1) {
            viewModel.navigateTo(Screen.UserProfile(owner))
            viewModel.loadUserProfile(owner)
            return true
        }
        val repo = pathSegments[1]
        if (pathSegments.size == 2) {
            viewModel.navigateTo(Screen.RepositoryDetail(owner, repo))
            viewModel.loadRepositoryDetails(owner, repo)
            return true
        }
        val branch = pathSegments[2]
        if (pathSegments.size >= 4) {
            val filePath = pathSegments.subList(3, pathSegments.size).joinToString("/")
            viewModel.navigateTo(Screen.FileView(owner, repo, filePath, cleanUrl))
            return true
        } else {
            viewModel.navigateTo(Screen.RepositoryDetail(owner, repo))
            viewModel.loadRepositoryDetails(owner, repo)
            return true
        }
    }
    
    if (pathSegments.isEmpty()) {
        viewModel.navigateTo(Screen.Dashboard)
        return true
    }
    
    val firstSegment = pathSegments[0]
    val reserved = setOf(
        "settings", "security", "search", "notifications", "explore", "trending", 
        "features", "about", "contact", "pricing", "marketplace", "login", "join", 
        "logout", "session", "site", "privacy", "terms", "cookies"
    )
    if (reserved.contains(firstSegment.lowercase())) {
        if (firstSegment.lowercase() == "settings") {
            viewModel.navigateTo(Screen.Settings)
            return true
        }
        if (firstSegment.lowercase() == "search") {
            val q = uri.getQueryParameter("q")
            if (!q.isNullOrBlank()) {
                viewModel.searchQuery.value = q
                viewModel.performSearch()
            }
            viewModel.navigateTo(Screen.Dashboard)
            return true
        }
        return false
    }
    
    if (pathSegments.size == 1) {
        val username = firstSegment
        viewModel.navigateTo(Screen.UserProfile(username))
        viewModel.loadUserProfile(username)
        return true
    }
    
    val owner = firstSegment
    val repo = pathSegments[1]
    
    if (pathSegments.size == 2) {
        viewModel.navigateTo(Screen.RepositoryDetail(owner, repo))
        viewModel.loadRepositoryDetails(owner, repo)
        return true
    }
    
    if (pathSegments.size >= 3) {
        val subAction = pathSegments[2].lowercase()
        when (subAction) {
            "issues" -> {
                viewModel.navigateTo(Screen.RepositoryDetail(owner, repo))
                viewModel.loadRepositoryDetails(owner, repo)
                viewModel.repoActiveTab.value = "ISSUES"
                viewModel.loadIssues("open")
                return true
            }
            "commits" -> {
                viewModel.navigateTo(Screen.RepositoryDetail(owner, repo))
                viewModel.loadRepositoryDetails(owner, repo)
                viewModel.repoActiveTab.value = "COMMITS"
                viewModel.loadCommits()
                return true
            }
            "releases" -> {
                viewModel.navigateTo(Screen.RepositoryDetail(owner, repo))
                viewModel.loadRepositoryDetails(owner, repo)
                viewModel.repoActiveTab.value = "RELEASES"
                viewModel.loadReleases()
                return true
            }
            "blob", "raw" -> {
                if (pathSegments.size >= 5) {
                    val branch = pathSegments[3]
                    val filePath = pathSegments.subList(4, pathSegments.size).joinToString("/")
                    val rawUrl = "https://raw.githubusercontent.com/$owner/$repo/$branch/$filePath"
                    viewModel.navigateTo(Screen.FileView(owner, repo, filePath, rawUrl))
                    return true
                }
            }
        }
        
        viewModel.navigateTo(Screen.RepositoryDetail(owner, repo))
        viewModel.loadRepositoryDetails(owner, repo)
        return true
    }
    
    return false
}

private object SyntaxAndMarkdownConstants {
    val tokenRegex = """(//.*|#.*|"(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*'|`(?:[^`\\]|\\.)*`|@[a-zA-Z_]\w*|[a-zA-Z_]\w*|0x[0-9a-fA-F]+|\b\d+(?:\.\d+)?\b|->|::|==|!=|<=|>=|&&|\|\||[-+*/%=<>!&|^~]+)""".toRegex()

    val kotlinJavaKeywords = setOf(
        "package", "import", "class", "interface", "object", "fun", "val", "var",
        "return", "if", "else", "for", "while", "when", "by", "this", "super",
        "private", "protected", "public", "internal", "override", "null", "true", "false",
        "enum", "sealed", "annotation", "constructor", "init", "throw", "try", "catch", "finally",
        "as", "is", "in", "out", "where", "typealias", "abstract", "final", "open", "const",
        "static", "void", "volatile", "transient", "synchronized", "strictfp"
    )

    val pythonKeywords = setOf(
        "def", "class", "import", "from", "as", "return", "if", "elif", "else",
        "for", "while", "in", "is", "not", "and", "or", "try", "except", "lambda", "None", "True", "False",
        "with", "yield", "finally", "assert", "break", "continue", "del", "global", "nonlocal", "pass", "raise"
    )

    val javascriptKeywords = setOf(
        "const", "let", "var", "function", "return", "if", "else", "for", "while",
        "class", "export", "import", "from", "default", "null", "true", "false", "undefined",
        "try", "catch", "finally", "switch", "case", "break", "continue", "throw", "new", "this",
        "typeof", "instanceof", "in", "of", "async", "await", "yield", "debugger", "extends", "super"
    )

    val cppKeywords = setOf(
        "class", "struct", "void", "return", "if", "else", "for", "while", "public", "private", "protected",
        "int", "float", "double", "char", "bool", "long", "short", "signed", "unsigned", "const", "volatile",
        "static", "extern", "register", "auto", "inline", "virtual", "explicit", "friend", "typename", "template",
        "namespace", "using", "try", "catch", "throw", "new", "delete", "operator", "this", "true", "false", "nullptr",
        "switch", "case", "default", "break", "continue", "goto", "typedef", "union"
    )

    val rustKeywords = setOf(
        "fn", "let", "mut", "const", "static", "impl", "trait", "struct", "enum", "union", "use", "mod", "pub",
        "return", "if", "else", "loop", "while", "for", "in", "match", "break", "continue", "unsafe", "where",
        "type", "as", "ref", "self", "Self", "true", "false", "move", "dyn", "async", "await", "crate"
    )

    val goKeywords = setOf(
        "package", "import", "func", "var", "const", "type", "struct", "interface", "map", "chan", "select",
        "return", "if", "else", "for", "range", "switch", "case", "default", "break", "continue", "goto", "fallthrough",
        "defer", "go", "nil", "true", "false"
    )

    val shellKeywords = setOf(
        "if", "then", "else", "elif", "fi", "case", "esac", "for", "while", "until", "do", "done", "in",
        "function", "return", "exit", "local", "export", "alias", "echo", "printf", "cd", "pwd", "set", "unset"
    )

    val sqlKeywords = setOf(
        "select", "from", "where", "and", "or", "not", "insert", "update", "delete", "into", "values", "set",
        "create", "table", "alter", "drop", "index", "view", "join", "inner", "left", "right", "outer", "on",
        "group", "by", "having", "order", "asc", "desc", "union", "all", "any", "exists", "in", "between", "like",
        "is", "null", "true", "false", "primary", "key", "foreign", "references", "distinct", "limit", "offset"
    )

    val builtinTypes = setOf(
        "String", "Int", "Boolean", "Long", "Double", "Float", "Char", "Byte", "Short", "Unit", "Any", "Nothing",
        "List", "Map", "Set", "ArrayList", "HashMap", "HashSet", "Array", "StateFlow", "MutableStateFlow",
        "void", "int", "boolean", "long", "double", "float", "char", "byte", "short",
        "string", "number", "object", "symbol", "any", "unknown", "never",
        "vector", "string_view", "unique_ptr", "shared_ptr", "make_unique", "make_shared",
        "i8", "i16", "i32", "i64", "i128", "isize", "u8", "u16", "u32", "u64", "u128", "usize", "f32", "f64", "bool", "str", "Option", "Result", "Box", "Rc", "Arc", "Vec"
    )

    // Precompiled Regex patterns for Markdown & Html parsing
    val commentRegex = Regex("<!--[\\s\\S]*?-->")
    val header6Regexes = (1..6).map { i -> Regex("""<h$i[^>]*>([\s\S]*?)</h$i>""", RegexOption.IGNORE_CASE) }
    val boldStyleRegex = Regex("""<(?:b|strong)[^>]*>([\s\S]*?)</(?:b|strong)>""", RegexOption.IGNORE_CASE)
    val italicStyleRegex = Regex("""<(?:i|em)[^>]*>([\s\S]*?)</(?:i|em)>""", RegexOption.IGNORE_CASE)
    val codeStyleRegex = Regex("""<code[^>]*>([\s\S]*?)</code>""", RegexOption.IGNORE_CASE)
    val kbdStyleRegex = Regex("""<(?:kbd|samp|var)[^>]*>(.*?)</(?:kbd|samp|var)>""", RegexOption.IGNORE_CASE)
    val imageHtmlRegex = Regex("""<img\s+[^>]*src\s*=\s*["']?([^"' >]+)["']?[^>]*>""", RegexOption.IGNORE_CASE)
    val altPropertyRegex = Regex("""alt\s*=\s*["']?([^"' >]+)["']?""", RegexOption.IGNORE_CASE)
    val anchorHtmlRegex = Regex("""<a\s+[^>]*href\s*=\s*["']?([^"' >]+)["']?[^>]*>([\s\S]*?)</a>""", RegexOption.IGNORE_CASE)
    
    val brTagRegex = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)
    val liTagRegex = Regex("""<li[^>]*>""", RegexOption.IGNORE_CASE)
    val trTagRegex = Regex("""<tr[^>]*>""", RegexOption.IGNORE_CASE)
    val tdTagRegex = Regex("""<td[^>]*>""", RegexOption.IGNORE_CASE)
    val thTagRegex = Regex("""<th[^>]*>""", RegexOption.IGNORE_CASE)
    val pTagOpenRegex = Regex("""<p[^>]*>""", RegexOption.IGNORE_CASE)
    val pTagCloseRegex = Regex("""</p>""", RegexOption.IGNORE_CASE)
    val divTagOpenRegex = Regex("""<div[^>]*>""", RegexOption.IGNORE_CASE)
    val divTagCloseRegex = Regex("""</div>""", RegexOption.IGNORE_CASE)
    val anyTagRegex = Regex("""<[a-zA-Z/][^>]*>""", RegexOption.IGNORE_CASE)

    val numberedListRegex = Regex("^\\d+\\.\\s+.*")
    val linkedImgRegex = Regex("""^\[!\[([^\]]+)\]\(([^)]+)\)\]\(([^)]+)\)$""")
    val imgOnlyRegex = Regex("""^!\[([^\]]+)\]\(([^)]+)\)$""")

    // Additional inline Markdown regexes
    val inlineBoldStrongRegex = Regex("<(?:b|strong)[^>]*>(.*?)</(?:b|strong)>", RegexOption.IGNORE_CASE)
    val inlineItalicEmRegex = Regex("<(?:i|em)[^>]*>(.*?)</(?:i|em)>", RegexOption.IGNORE_CASE)
    val inlineCodeRegexSoft = Regex("<code[^>]*>(.*?)</code>", RegexOption.IGNORE_CASE)
    val inlineKbdStyleRegexSoft = Regex("<(?:kbd|samp|var)[^>]*>(.*?)</(?:kbd|samp|var)>", RegexOption.IGNORE_CASE)
}

@Composable
fun OptimizedAvatar(
    url: String?,
    contentDescription: String,
    modifier: Modifier,
    quality: String
) {
    if (quality == "DISABLE" || quality == "OFF") {
        Box(
            modifier = modifier.background(Color(0xFF111111)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Ø",
                color = Color(0xFF00E5FF),
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Default,
                fontSize = 11.sp
            )
        }
    } else {
        val model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
            .data(url)
            .memoryCacheKey("${url}_cached")
            .diskCacheKey("${url}_cached")
            .build()
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            modifier = modifier
        )
    }
}

fun highlightCode(code: String, fileName: String, searchQuery: String = ""): AnnotatedString {
    val extension = fileName.substringAfterLast('.', "").lowercase()

    if (extension in setOf("xml", "html", "xhtml", "svg")) {
        return highlightXmlHtml(code, searchQuery)
    }

    if (extension == "md") {
        return highlightMarkdown(code, searchQuery)
    }

    if (extension in setOf("yaml", "yml", "properties", "ini", "toml", "conf")) {
        return highlightConfig(code, extension, searchQuery)
    }

    val builder = AnnotatedString.Builder()
    
    val keywords = when (extension) {
        "kt", "kts", "java" -> SyntaxAndMarkdownConstants.kotlinJavaKeywords
        "py" -> SyntaxAndMarkdownConstants.pythonKeywords
        "js", "ts", "json" -> SyntaxAndMarkdownConstants.javascriptKeywords
        "cpp", "c", "h", "hpp", "cc", "cxx" -> SyntaxAndMarkdownConstants.cppKeywords
        "rs" -> SyntaxAndMarkdownConstants.rustKeywords
        "go" -> SyntaxAndMarkdownConstants.goKeywords
        "sh", "bash" -> SyntaxAndMarkdownConstants.shellKeywords
        "sql" -> SyntaxAndMarkdownConstants.sqlKeywords
        else -> SyntaxAndMarkdownConstants.kotlinJavaKeywords
    }

    val builtinTypes = SyntaxAndMarkdownConstants.builtinTypes

    val lines = code.lines()
    var inMultilineComment = false
    
    lines.forEachIndexed { lineIndex, line ->
        if (inMultilineComment) {
            val endIdx = line.indexOf("*/")
            if (endIdx != -1) {
                builder.pushStyle(SpanStyle(color = Color(0xFF6272A4), fontStyle = androidx.compose.ui.text.font.FontStyle.Italic))
                builder.append(line.substring(0, endIdx + 2))
                builder.pop()
                inMultilineComment = false
                
                // Parse the rest of the line
                val rest = line.substring(endIdx + 2)
                tokenizeLine(rest, keywords, builtinTypes, builder, line, endIdx + 2)
            } else {
                builder.pushStyle(SpanStyle(color = Color(0xFF6272A4), fontStyle = androidx.compose.ui.text.font.FontStyle.Italic))
                builder.append(line)
                builder.pop()
            }
        } else {
            val startIdx = line.indexOf("/*")
            if (startIdx != -1) {
                // Check if there is an end on the same line
                val endIdx = line.indexOf("*/", startIdx + 2)
                if (endIdx != -1) {
                    // Tokenize before block comment
                    tokenizeLine(line.substring(0, startIdx), keywords, builtinTypes, builder, line, 0)
                    
                    // Style the block comment
                    builder.pushStyle(SpanStyle(color = Color(0xFF6272A4), fontStyle = androidx.compose.ui.text.font.FontStyle.Italic))
                    builder.append(line.substring(startIdx, endIdx + 2))
                    builder.pop()
                    
                    // Tokenize after block comment
                    tokenizeLine(line.substring(endIdx + 2), keywords, builtinTypes, builder, line, endIdx + 2)
                } else {
                    // Tokenize before block comment
                    tokenizeLine(line.substring(0, startIdx), keywords, builtinTypes, builder, line, 0)
                    
                    // Comment from startIdx to end of line
                    builder.pushStyle(SpanStyle(color = Color(0xFF6272A4), fontStyle = androidx.compose.ui.text.font.FontStyle.Italic))
                    builder.append(line.substring(startIdx))
                    builder.pop()
                    inMultilineComment = true
                }
            } else {
                tokenizeLine(line, keywords, builtinTypes, builder, line, 0)
            }
        }
        
        if (lineIndex < lines.size - 1) {
            builder.append("\n")
        }
    }
    
    return applySearchHighlight(builder.toAnnotatedString(), searchQuery)
}

fun tokenizeLine(
    line: String,
    keywords: Set<String>,
    builtinTypes: Set<String>,
    builder: AnnotatedString.Builder,
    originalLine: String,
    offsetInOriginalLine: Int
) {
    if (line.isEmpty()) return
    
    // Improved regex tokenizer matching comments, strings, annotations, numbers, operators and identifiers
    val matches = SyntaxAndMarkdownConstants.tokenRegex.findAll(line)
    var lastPos = 0
    for (match in matches) {
        if (match.range.first > lastPos) {
            builder.append(line.substring(lastPos, match.range.first))
        }
        
        val token = match.value
        when {
            // Line comment
            token.startsWith("//") || token.startsWith("#") -> {
                builder.pushStyle(SpanStyle(color = Color(0xFF6272A4), fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) // Dracula comment
                builder.append(token)
                builder.pop()
            }
            // Strings
            token.startsWith("\"") || token.startsWith("'") || token.startsWith("`") -> {
                builder.pushStyle(SpanStyle(color = Color(0xFFF1FA8C))) // Dracula Yellow String
                builder.append(token)
                builder.pop()
            }
            // Annotations / Decorators
            token.startsWith("@") -> {
                builder.pushStyle(SpanStyle(color = Color(0xFFFFB86C))) // Dracula Orange Annotations
                builder.append(token)
                builder.pop()
            }
            // Numbers
            token.first().isDigit() || token.startsWith("0x") -> {
                builder.pushStyle(SpanStyle(color = Color(0xFFBD93F9))) // Dracula Purple Number
                builder.append(token)
                builder.pop()
            }
            // Operators & Special character sequences
            token in setOf("->", "::", "==", "!=", "<=", ">=", "&&", "||", "=", "+", "-", "*", "/", "%", "!", "?", ":", ".", "<", ">", "&", "|", "^", "~") -> {
                builder.pushStyle(SpanStyle(color = Color(0xFFFF79C6))) // Dracula Pink Operator
                builder.append(token)
                builder.pop()
            }
            // Identifiers
            token[0].isLetter() || token[0] == '_' -> {
                // Check literal constants
                val constants = setOf("this", "super", "null", "true", "false", "None", "True", "False", "nil", "undefined")
                when {
                    token in constants -> {
                        builder.pushStyle(SpanStyle(color = Color(0xFFBD93F9))) // Dracula Purple for literals
                        builder.append(token)
                        builder.pop()
                    }
                    token in keywords -> {
                        builder.pushStyle(SpanStyle(color = Color(0xFFFF79C6), fontWeight = FontWeight.Bold)) // Dracula Pink Keyword
                        builder.append(token)
                        builder.pop()
                    }
                    token in builtinTypes || token[0].isUpperCase() -> {
                        builder.pushStyle(SpanStyle(color = Color(0xFF8BE9FD))) // Dracula Cyan Type/Class
                        builder.append(token)
                        builder.pop()
                    }
                    // Lookahead check for function call
                    originalLine.substring(offsetInOriginalLine + match.range.last + 1).trimStart().startsWith("(") -> {
                        builder.pushStyle(SpanStyle(color = Color(0xFF50FA7B))) // Dracula Green Function
                        builder.append(token)
                        builder.pop()
                    }
                    else -> {
                        builder.append(token)
                    }
                }
            }
            else -> {
                builder.append(token)
            }
        }
        lastPos = match.range.last + 1
    }
    if (lastPos < line.length) {
        builder.append(line.substring(lastPos))
    }
}

fun highlightXmlHtml(code: String, searchQuery: String = ""): AnnotatedString {
    val builder = AnnotatedString.Builder()
    val pink = Color(0xFFFF79C6)  // Tags brackets
    val red = Color(0xFFFF5555)   // Tag names
    val orange = Color(0xFFFFB86C)// Attribute names
    val yellow = Color(0xFFF1FA8C)// Attribute values
    val comment = Color(0xFF6272A4)// Comments
    
    val lines = code.lines()
    var inComment = false
    
    lines.forEachIndexed { lineIndex, line ->
        var i = 0
        val len = line.length
        
        while (i < len) {
            if (inComment) {
                val endCommentIndex = line.indexOf("-->", i)
                if (endCommentIndex != -1) {
                    builder.pushStyle(SpanStyle(color = comment, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic))
                    builder.append(line.substring(i, endCommentIndex + 3))
                    builder.pop()
                    i = endCommentIndex + 3
                    inComment = false
                } else {
                    builder.pushStyle(SpanStyle(color = comment, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic))
                    builder.append(line.substring(i))
                    builder.pop()
                    i = len
                }
            } else {
                // Look for comment start
                if (line.startsWith("<!--", i) || (i + 4 <= len && line.substring(i, i + 4) == "<!--")) {
                    inComment = true
                    builder.pushStyle(SpanStyle(color = comment, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic))
                    val endCommentIndex = line.indexOf("-->", i + 4)
                    if (endCommentIndex != -1) {
                        builder.append(line.substring(i, endCommentIndex + 3))
                        builder.pop()
                        i = endCommentIndex + 3
                        inComment = false
                    } else {
                        builder.append(line.substring(i))
                        builder.pop()
                        i = len
                    }
                } else {
                    // Match a tag
                    if (line[i] == '<') {
                        builder.pushStyle(SpanStyle(color = pink)) // tag start '<' or '</'
                        if (i + 1 < len && line[i + 1] == '/') {
                            builder.append("</")
                            i += 2
                        } else {
                            builder.append("<")
                            i += 1
                        }
                        builder.pop()
                        
                        // Parse Tag Name
                        val tagNameStart = i
                        while (i < len && line[i] != '>' && line[i] != '/' && !line[i].isWhitespace()) {
                            i++
                        }
                        if (i > tagNameStart) {
                            builder.pushStyle(SpanStyle(color = red, fontWeight = FontWeight.Bold))
                            builder.append(line.substring(tagNameStart, i))
                            builder.pop()
                        }
                        
                        // Parse Attributes inside Tag
                        while (i < len && line[i] != '>' && (line[i] != '/' || (i + 1 < len && line[i + 1] != '>'))) {
                            val loopStartI = i
                            if (line[i].isWhitespace()) {
                                builder.append(line[i].toString())
                                i++
                                continue
                            }
                            // Attribute Name
                            val attrNameStart = i
                            while (i < len && line[i] != '=' && !line[i].isWhitespace() && line[i] != '>' && line[i] != '/') {
                                i++
                            }
                            if (i > attrNameStart) {
                                builder.pushStyle(SpanStyle(color = orange))
                                builder.append(line.substring(attrNameStart, i))
                                builder.pop()
                            }
                            
                            // Attribute Equals '='
                            while (i < len && line[i].isWhitespace()) {
                                builder.append(line[i].toString())
                                i++
                            }
                            if (i < len && line[i] == '=') {
                                builder.pushStyle(SpanStyle(color = pink))
                                builder.append("=")
                                builder.pop()
                                i++
                            } else {
                                if (i == loopStartI) {
                                    builder.append(line[i].toString())
                                    i++
                                }
                                continue
                            }
                            
                            // Attribute Value (String)
                            while (i < len && line[i].isWhitespace()) {
                                builder.append(line[i].toString())
                                i++
                            }
                            if (i < len && (line[i] == '"' || line[i] == '\'')) {
                                val quote = line[i]
                                val valStart = i
                                i++ // skip starting quote
                                while (i < len && line[i] != quote) {
                                    // handle escaped char
                                    if (line[i] == '\\' && i + 1 < len) {
                                        i += 2
                                    } else {
                                        i++
                                    }
                                }
                                if (i < len) {
                                    i++ // skip ending quote
                                }
                                builder.pushStyle(SpanStyle(color = yellow))
                                builder.append(line.substring(valStart, i))
                                builder.pop()
                            }
                        }
                        
                        // Parse Tag End '>' or '/>'
                        if (i < len && line[i] == '/') {
                            builder.pushStyle(SpanStyle(color = pink))
                            builder.append("/")
                            builder.pop()
                            i++
                        }
                        if (i < len && line[i] == '>') {
                            builder.pushStyle(SpanStyle(color = pink))
                            builder.append(">")
                            builder.pop()
                            i++
                        }
                    } else {
                        // Standard XML plain text / content
                        builder.append(line[i].toString())
                        i++
                    }
                }
            }
        }
        if (lineIndex < lines.size - 1) {
            builder.append("\n")
        }
    }
    
    return applySearchHighlight(builder.toAnnotatedString(), searchQuery)
}

fun highlightMarkdown(code: String, searchQuery: String = ""): AnnotatedString {
    val builder = AnnotatedString.Builder()
    val pink = Color(0xFFFF79C6)
    val purple = Color(0xFFBD93F9)
    val green = Color(0xFF50FA7B)
    val cyan = Color(0xFF8BE9FD)
    val yellow = Color(0xFFF1FA8C)
    val comment = Color(0xFF6272A4)
    val orange = Color(0xFFFFB86C)
    
    val lines = code.lines()
    var inCodeBlock = false
    
    lines.forEachIndexed { lineIndex, line ->
        val trimmed = line.trim()
        if (trimmed.startsWith("```")) {
            inCodeBlock = !inCodeBlock
            builder.pushStyle(SpanStyle(color = comment, fontWeight = FontWeight.Bold))
            builder.append(line)
            builder.pop()
        } else if (inCodeBlock) {
            builder.pushStyle(SpanStyle(color = Color(0xFFE2E2E2)))
            builder.append(line)
            builder.pop()
        } else {
            if (line.startsWith("#")) {
                val headLevel = line.takeWhile { it == '#' }.length
                if (headLevel in 1..6 && line.getOrNull(headLevel) == ' ') {
                    builder.pushStyle(SpanStyle(color = purple, fontWeight = FontWeight.Bold))
                    builder.append(line.take(headLevel + 1))
                    builder.pop()
                    builder.pushStyle(SpanStyle(color = cyan, fontWeight = FontWeight.ExtraBold))
                    builder.append(line.drop(headLevel + 1))
                    builder.pop()
                } else {
                    builder.append(line)
                }
            } else if (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ")) {
                val bulletWidth = line.indexOfAny(charArrayOf('-', '*', '+')) + 2
                builder.pushStyle(SpanStyle(color = pink, fontWeight = FontWeight.Bold))
                builder.append(line.take(bulletWidth))
                builder.pop()
                builder.append(line.drop(bulletWidth))
            } else if (trimmed.startsWith("> ")) {
                builder.pushStyle(SpanStyle(color = comment, fontWeight = FontWeight.Bold, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic))
                builder.append("> ")
                builder.pop()
                builder.append(line.drop(2))
            } else {
                var i = 0
                val len = line.length
                while (i < len) {
                    when {
                        line.startsWith("`", i) -> {
                            val nextTick = line.indexOf('`', i + 1)
                            if (nextTick != -1) {
                                builder.pushStyle(SpanStyle(color = green, background = Color(0xFF1E1F29)))
                                builder.append(line.substring(i, nextTick + 1))
                                builder.pop()
                                i = nextTick + 1
                            } else {
                                builder.append("`")
                                i++
                            }
                        }
                        line.startsWith("**", i) -> {
                            val nextDoubleAsterisk = line.indexOf("**", i + 2)
                            if (nextDoubleAsterisk != -1) {
                                builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = orange))
                                builder.append(line.substring(i, nextDoubleAsterisk + 2))
                                builder.pop()
                                i = nextDoubleAsterisk + 2
                            } else {
                                builder.append("**")
                                i += 2
                            }
                        }
                        line.startsWith("[", i) -> {
                            val endBracket = line.indexOf("]", i + 1)
                            if (endBracket != -1 && endBracket + 1 < len && line[endBracket + 1] == '(') {
                                val endParen = line.indexOf(")", endBracket + 2)
                                if (endParen != -1) {
                                    val text = line.substring(i + 1, endBracket)
                                    val url = line.substring(endBracket + 2, endParen)
                                    
                                    builder.pushStyle(SpanStyle(color = pink))
                                    builder.append("[")
                                    builder.pop()
                                    
                                    builder.pushStyle(SpanStyle(color = green, fontWeight = FontWeight.Bold))
                                    builder.append(text)
                                    builder.pop()
                                    
                                    builder.pushStyle(SpanStyle(color = pink))
                                    builder.append("](")
                                    builder.pop()
                                    
                                    builder.pushStyle(SpanStyle(color = cyan))
                                    builder.append(url)
                                    builder.pop()
                                    
                                    builder.pushStyle(SpanStyle(color = pink))
                                    builder.append(")")
                                    builder.pop()
                                    
                                    i = endParen + 1
                                    continue
                                }
                            }
                            builder.append("[")
                            i++
                        }
                        else -> {
                            builder.append(line[i].toString())
                            i++
                        }
                    }
                }
            }
        }
        
        if (lineIndex < lines.size - 1) {
            builder.append("\n")
        }
    }
    
    return applySearchHighlight(builder.toAnnotatedString(), searchQuery)
}

fun highlightConfig(code: String, extension: String, searchQuery: String = ""): AnnotatedString {
    val builder = AnnotatedString.Builder()
    val lines = code.lines()
    val pink = Color(0xFFFF79C6)
    val cyan = Color(0xFF8BE9FD)
    val yellow = Color(0xFFF1FA8C)
    val purple = Color(0xFFBD93F9)
    val commentColor = Color(0xFF6272A4)
    
    lines.forEachIndexed { lineIndex, line ->
        val trimmed = line.trim()
        if (trimmed.startsWith("#") || trimmed.startsWith(";")) {
            builder.pushStyle(SpanStyle(color = commentColor, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic))
            builder.append(line)
            builder.pop()
        } else {
            val delimiter = if (extension == "properties") "=" else ":"
            val index = line.indexOf(delimiter)
            if (index != -1 && !trimmed.startsWith("[") && !trimmed.startsWith("#")) {
                val key = line.substring(0, index)
                val value = line.substring(index + 1)
                
                builder.pushStyle(SpanStyle(color = cyan, fontWeight = FontWeight.Bold))
                builder.append(key)
                builder.pop()
                
                builder.pushStyle(SpanStyle(color = pink))
                builder.append(delimiter)
                builder.pop()
                
                // Highlight the value
                val trimmedVal = value.trim()
                if (trimmedVal.startsWith("\"") || trimmedVal.startsWith("'")) {
                    builder.pushStyle(SpanStyle(color = yellow))
                    builder.append(value)
                    builder.pop()
                } else if (trimmedVal.toIntOrNull() != null || trimmedVal.toDoubleOrNull() != null || trimmedVal.lowercase() in setOf("true", "false", "null", "yes", "no")) {
                    builder.pushStyle(SpanStyle(color = purple))
                    builder.append(value)
                    builder.pop()
                } else {
                    builder.append(value)
                }
            } else if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                builder.pushStyle(SpanStyle(color = pink, fontWeight = FontWeight.Bold))
                builder.append(line)
                builder.pop()
            } else {
                builder.append(line)
            }
        }
        if (lineIndex < lines.size - 1) {
            builder.append("\n")
        }
    }
    return applySearchHighlight(builder.toAnnotatedString(), searchQuery)
}

fun applySearchHighlight(annotatedString: AnnotatedString, searchQuery: String): AnnotatedString {
    if (searchQuery.isEmpty()) return annotatedString
    val finalBuilder = AnnotatedString.Builder(annotatedString)
    val text = annotatedString.text
    var index = text.indexOf(searchQuery, ignoreCase = true)
    while (index != -1) {
        finalBuilder.addStyle(
            style = SpanStyle(
                background = Color(0xFFF1FA8C), // Dracula Yellow
                color = Color(0xFF282A36), // High contrast dark text inside match
                fontWeight = FontWeight.Bold
            ),
            start = index,
            end = index + searchQuery.length
        )
        index = text.indexOf(searchQuery, index + searchQuery.length, ignoreCase = true)
    }
    return finalBuilder.toAnnotatedString()
}

/**
 * Highlights all occurrences of [query] in [text] with a yellow background.
 * The default color is applied via the Text composable's color parameter —
 * this only overrides the match spans with a dark foreground on yellow bg.
 */
private fun highlightSearchAnnotated(text: String, query: String): AnnotatedString {
    if (query.isBlank()) return AnnotatedString(text)
    val builder = AnnotatedString.Builder(text)
    var index = text.indexOf(query, ignoreCase = true)
    while (index != -1) {
        builder.addStyle(
            style = SpanStyle(
                background = Color(0xFFF1FA8C),
                color = Color(0xFF282A36),
                fontWeight = FontWeight.Bold
            ),
            start = index,
            end = index + query.length
        )
        index = text.indexOf(query, index + query.length, ignoreCase = true)
    }
    return builder.toAnnotatedString()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GH4AAppContent(viewModel: GitHubViewModel) {
    CompositionLocalProvider(LocalGitHubViewModel provides viewModel) {
        val currentScreen by viewModel.currentScreen.collectAsState()
        val canGoBack by viewModel.isBackEnabled.collectAsState()

        BackHandler(enabled = canGoBack) {
            viewModel.navigateBack()
        }

        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val titleText = when (currentScreen) {
                                is Screen.FileView -> (currentScreen as Screen.FileView).filePath.substringAfterLast('/')
                                is Screen.RepositoryDetail -> (currentScreen as Screen.RepositoryDetail).name
                                is Screen.UserProfile -> (currentScreen as Screen.UserProfile).username
                                else -> "GITDROID"
                            }
                            Text(
                                text = titleText,
                                color = Color(0xFF00E5FF),
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Default,
                                fontSize = 20.sp
                            )
                        }
                    },
                    navigationIcon = {
                        if (canGoBack) {
                            IconButton(
                                onClick = { viewModel.navigateBack() }
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color(0xFF00E5FF)
                                )
                            }
                        } else {
                            IconButton(onClick = {}) {
                                Icon(
                                    imageVector = Icons.Default.Terminal,
                                    contentDescription = "Terminal Logo",
                                    tint = Color(0xFF00E5FF)
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.navigateTo(Screen.Settings) }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = if (currentScreen is Screen.Settings) Color(0xFF00E5FF) else Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black,
                        titleContentColor = Color.White
                    )
                )
            },
            bottomBar = {
                // High-contrast clean bottom navigation tab, extremely snappy and instant
                NavigationBar(
                    containerColor = Color.Black,
                    modifier = Modifier.border(width = 1.dp, color = Color(0xFF1E1E1E), shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp)),
                    windowInsets = WindowInsets.navigationBars
                ) {
                    NavigationBarItem(
                        selected = currentScreen is Screen.Dashboard,
                        onClick = { viewModel.navigateTo(Screen.Dashboard) },
                        icon = { Icon(Icons.Default.Explore, contentDescription = "Explore") },
                        label = { Text("Explore", fontFamily = FontFamily.Default) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Black,
                            selectedTextColor = Color(0xFF00E5FF),
                            indicatorColor = Color(0xFF00E5FF),
                            unselectedIconColor = Color.White.copy(alpha = 0.5f),
                            unselectedTextColor = Color.White.copy(alpha = 0.5f)
                        )
                    )

                    val hostUser by viewModel.activeHostUser.collectAsState()
                    val safeHostUser = hostUser
                    val hasHostUser = !safeHostUser.isNullOrBlank()
                    NavigationBarItem(
                        selected = currentScreen is Screen.UserProfile && (currentScreen as Screen.UserProfile).username == safeHostUser,
                        onClick = {
                            if (hasHostUser) {
                                viewModel.navigateTo(Screen.UserProfile(safeHostUser))
                                viewModel.loadUserProfile(safeHostUser)
                            }
                        },
                        icon = { Icon(Icons.Default.AccountCircle, contentDescription = "Profile") },
                        label = { Text("My Profile", fontFamily = FontFamily.Default) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Black,
                            selectedTextColor = Color(0xFF00E5FF),
                            indicatorColor = Color(0xFF00E5FF),
                            unselectedIconColor = Color.White.copy(alpha = 0.5f),
                            unselectedTextColor = Color.White.copy(alpha = 0.5f)
                        )
                    )

                    NavigationBarItem(
                        selected = currentScreen is Screen.Settings,
                        onClick = { viewModel.navigateTo(Screen.Settings) },
                        icon = { Icon(Icons.Default.Lock, contentDescription = "API Token") },
                        label = { Text("API Token", fontFamily = FontFamily.Default) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Black,
                            selectedTextColor = Color(0xFF00E5FF),
                            indicatorColor = Color(0xFF00E5FF),
                            unselectedIconColor = Color.White.copy(alpha = 0.5f),
                            unselectedTextColor = Color.White.copy(alpha = 0.5f)
                        )
                    )
                }
            }
        ) { innerPadding ->
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(Color.Black),
                color = Color.Black
            ) {
                // Direct, instant, state-branched screen flipping (zero animations or delay handlers)
                when (val screen = currentScreen) {
                    is Screen.Dashboard -> DashboardScreen(viewModel = viewModel)
                    is Screen.RepositoryDetail -> RepositoryDetailScreen(viewModel = viewModel, owner = screen.owner, name = screen.name)
                    is Screen.UserProfile -> UserProfileScreen(viewModel = viewModel, username = screen.username)
                    is Screen.Settings -> SettingsScreen(viewModel = viewModel)
                    is Screen.FileView -> FileViewScreen(
                        viewModel = viewModel,
                        owner = screen.owner,
                        repo = screen.repo,
                        path = screen.filePath,
                        downloadUrl = screen.downloadUrl
                    )
                }
            }
        }
    }
}

@Composable
fun DashboardScreen(viewModel: GitHubViewModel) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchType by viewModel.searchType.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val searchReposResult by viewModel.searchReposResult.collectAsState()
    val searchUsersResult by viewModel.searchUsersResult.collectAsState()
    val searchError by viewModel.searchError.collectAsState()
    val imageQuality by viewModel.imageQuality.collectAsState()

    val recentSearches by viewModel.recentSearches.collectAsState()
    val bookmarkedRepos by viewModel.bookmarkedRepos.collectAsState()

    val keyboardController = LocalSoftwareKeyboardController.current

    val scrollKey = "dashboard_list"
    val savedState = viewModel.getScrollState(scrollKey)
    val listState = rememberLazyListState()
    
    // Restore scroll position after data is loaded (handle case where list is empty at composition time)
    val hasAnyData = (searchQuery.isNotEmpty() && (searchReposResult.isNotEmpty() || searchUsersResult.isNotEmpty())) ||
                     (searchQuery.isEmpty() && (bookmarkedRepos.isNotEmpty() || recentSearches.isNotEmpty()))
    LaunchedEffect(listState, hasAnyData) {
        if (hasAnyData) {
            val targetIndex = savedState.first.coerceAtLeast(0)
            val targetOffset = savedState.second.coerceAtLeast(0)
            if (targetIndex > 0 || targetOffset > 0) {
                listState.scrollToItem(targetIndex, targetOffset)
            }
        }
    }
    
    DisposableEffect(listState) {
        onDispose {
            viewModel.saveScrollState(scrollKey, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
    ) {
        // Search & Toggle Widget
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF00E5FF), RoundedCornerShape(8.dp))
                    .background(Color(0xFF0A0A0A))
                    .padding(12.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.searchQuery.value = it },
                    label = { Text("Query github...") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00E5FF),
                        unfocusedBorderColor = Color(0xFF333333),
                        focusedLabelColor = Color(0xFF00E5FF),
                        unfocusedLabelColor = Color.Gray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color.Black,
                        unfocusedContainerColor = Color.Black
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        keyboardController?.hide()
                        viewModel.performSearch()
                    }),
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.clearSearch() }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.Gray)
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Snappy Tab Segment selector
                    Row(
                        modifier = Modifier
                            .border(1.dp, Color(0xFF333333), RoundedCornerShape(0.dp))
                            .background(Color.Black),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isRepoSelected = searchType == "REPO"
                        Box(
                            modifier = Modifier
                                .background(if (isRepoSelected) Color(0xFF00E5FF) else Color.Transparent)
                                .clickable { viewModel.searchType.value = "REPO" }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                "Repos",
                                color = if (isRepoSelected) Color.Black else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Default
                            )
                        }
                        Box(
                            modifier = Modifier
                                .background(if (!isRepoSelected) Color(0xFF00E5FF) else Color.Transparent)
                                .clickable { viewModel.searchType.value = "USER" }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                "Users",
                                color = if (!isRepoSelected) Color.Black else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Default
                            )
                        }
                    }

                    Button(
                        onClick = {
                            keyboardController?.hide()
                            viewModel.performSearch()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00E5FF),
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            "RUN",
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Default,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // Searching Loader
        if (isSearching) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF00E5FF))
                }
            }
        }

        // Errors display
        searchError?.let { err ->
            item {
                Text(
                    text = "Error: $err",
                    color = Color.Red,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Default,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }

        // Active Search Results
        if (searchQuery.isNotEmpty()) {
            if (searchType == "REPO" && searchReposResult.isNotEmpty()) {
                item {
                    Text(
                        "SEARCH OUTPUT",
                        color = Color(0xFF00E5FF),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Default
                    )
                }
                items(searchReposResult, key = { it.id }) { repo ->
                    val isPinned by remember(repo.id) {
                        derivedStateOf { bookmarkedRepos.any { it.id == repo.id } }
                    }
                    RepositoryRow(
                        repo = repo, 
                        onClick = {
                            keyboardController?.hide()
                            viewModel.selectRepository(repo.owner.login, repo.name)
                        },
                        isBookmarked = isPinned,
                        onBookmarkClick = {
                            if (isPinned) viewModel.removeBookmark(repo.id) else viewModel.addBookmark(repo)
                        }
                    )
                }
            } else if (searchType == "USER" && searchUsersResult.isNotEmpty()) {
                item {
                    Text(
                        "SEARCH OUTPUT (USERS)",
                        color = Color(0xFF00E5FF),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Default
                    )
                }
                items(searchUsersResult, key = { it.id }) { user ->
                    UserRow(user = user, quality = imageQuality, onClick = {
                        keyboardController?.hide()
                        viewModel.navigateTo(Screen.UserProfile(user.login))
                        viewModel.loadUserProfile(user.login)
                    })
                }
            }
        } else {
            // Dashboard Default state (Recent history & Bookmarks)
            
            // Local Bookmarks
            if (bookmarkedRepos.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "PINNED REPOSITORIES",
                            color = Color(0xFF00E5FF),
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Default
                        )
                    }
                }

                items(bookmarkedRepos, key = { it.id }) { bookmarked ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFF222222), RoundedCornerShape(8.dp))
                            .clickable {
                                keyboardController?.hide()
                                viewModel.selectRepository(bookmarked.ownerLogin, bookmarked.name)
                            },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF070707))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = bookmarked.fullName,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Star, contentDescription = "Stars", tint = Color(0xFF00E5FF), modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "${bookmarked.stargazersCount}",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Default
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    IconButton(
                                        onClick = { 
                                            // Provide explicit unpin functionality
                                            viewModel.removeBookmark(bookmarked.id)
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = "Unpin Repo",
                                            tint = Color(0xFF00E5FF)
                                        )
                                    }
                                }
                            }

                            if (!bookmarked.description.isNullOrEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = bookmarked.description,
                                    color = Color.Gray,
                                    fontSize = 12.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (!bookmarked.language.isNullOrEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                UnitBadge(label = bookmarked.language, color = Color(0xFF00E5FF))
                            }
                        }
                    }
                }
            }

            // Recent Search queries stored in Room
            if (recentSearches.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.History, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "RECENT SEARCH QUERIES",
                                color = Color.White.copy(alpha = 0.5f),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Default
                            )
                        }
                        Text(
                            "CLEAR ALL",
                            color = Color(0xFF00E5FF),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Default,
                            modifier = Modifier
                                .clickable { viewModel.clearSearchHistory() }
                                .padding(4.dp)
                        )
                    }
                }

                items(recentSearches, key = { it.localId }) { entity ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFF151515), RoundedCornerShape(4.dp))
                            .background(Color(0xFF030303))
                            .clickable {
                                keyboardController?.hide()
                                viewModel.searchQuery.value = entity.query
                                viewModel.searchType.value = entity.type
                                viewModel.performSearch()
                            }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (entity.type == "REPO") Icons.Default.Source else Icons.Default.Person,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = entity.query,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Default
                            )
                        }
                        IconButton(
                            modifier = Modifier.size(24.dp),
                            onClick = { viewModel.deleteHistoryItem(entity.localId) }
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Delete", tint = Color.Gray, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            // Quick lookup of custom repositories directly
            item {
                Text(
                    "DIRECT REPOSITORY ACCESS",
                    color = Color.White.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Default
                )
            }

            item {
                var directInput by remember { mutableStateOf("") }
                var directError by remember { mutableStateOf<String?>(null) }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFF222222), RoundedCornerShape(6.dp))
                        .background(Color(0xFF050505))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Access via owner/name:",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Default,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = directInput,
                        onValueChange = {
                            directInput = it
                            directError = null
                        },
                        placeholder = { Text("e.g. user/repo", fontSize = 13.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00E5FF),
                            unfocusedBorderColor = Color(0xFF222222),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color.Black,
                            unfocusedContainerColor = Color.Black
                        ),
                        singleLine = true
                    )
                    if (directError != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(directError!!, color = Color.Red, fontSize = 11.sp, fontFamily = FontFamily.Default)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            keyboardController?.hide()
                            val parts = directInput.trim().split("/")
                            if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                                viewModel.selectRepository(parts[0].trim(), parts[1].trim())
                            } else {
                                directError = "Invalid target format. Use 'owner/repository'."
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Black,
                            contentColor = Color(0xFF00E5FF)
                        ),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF00E5FF), RoundedCornerShape(4.dp))
                    ) {
                        Text("LOAD", fontWeight = FontWeight.Black, fontFamily = FontFamily.Default, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun RepositoryRow(
    repo: GitHubApiRepository, 
    onClick: () -> Unit,
    isBookmarked: Boolean = false,
    onBookmarkClick: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF1F1F1F), RoundedCornerShape(8.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF050505))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = repo.fullName,
                    color = Color(0xFF00E5FF),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                if (onBookmarkClick != null) {
                    IconButton(
                        onClick = onBookmarkClick,
                        modifier = Modifier.size(24.dp).padding(start = 8.dp)
                    ) {
                        Icon(
                            imageVector = if (isBookmarked) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = if (isBookmarked) "Remove Bookmark" else "Add Bookmark",
                            tint = Color(0xFF00E5FF)
                        )
                    }
                }
            }
            if (!repo.description.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = repo.description,
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, contentDescription = "Stars", tint = Color(0xFF00E5FF), modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "${repo.stargazersCount ?: 0}",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Default
                    )
                }

                if (!repo.language.isNullOrEmpty()) {
                    UnitBadge(label = repo.language, color = Color(0xFF00E5FF))
                }
            }
        }
    }
}

@Composable
fun UserRow(user: GitHubUser, quality: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF1A1A1A), RoundedCornerShape(8.dp))
            .background(Color(0xFF050505))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OptimizedAvatar(
            url = user.avatarUrl,
            contentDescription = "Avatar",
            modifier = Modifier
                .size(40.dp)
                .background(Color.DarkGray),
            quality = quality
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = user.login,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                fontFamily = FontFamily.Default
            )
            Text(
                text = "ID: ${user.id}",
                color = Color.Gray,
                fontSize = 11.sp,
                fontFamily = FontFamily.Default
            )
        }
    }
}

fun getLanguageColor(lang: String): Color {
    return when (lang.lowercase()) {
        "kotlin" -> Color(0xFFA97BFF)
        "java" -> Color(0xFFB07219)
        "python" -> Color(0xFF3572A5)
        "javascript", "js" -> Color(0xFFF1E05A)
        "typescript", "ts" -> Color(0xFF3178C6)
        "html" -> Color(0xFFE34C26)
        "css" -> Color(0xFF563D7C)
        "c++", "cpp" -> Color(0xFFF34B7D)
        "c" -> Color(0xFF555555)
        "swift" -> Color(0xFFF05138)
        "shell", "bash" -> Color(0xFF89E051)
        "rust" -> Color(0xFFDEA584)
        "go" -> Color(0xFF00ADD8)
        "ruby" -> Color(0xFF701516)
        else -> Color(0xFF8B949E)
    }
}

@Composable
fun RepositoryLanguagesBar(languages: Map<String, Long>, modifier: Modifier = Modifier) {
    if (languages.isEmpty()) return
    val totalBytes = languages.values.sum()
    if (totalBytes == 0L) return
    val sortedLanguages = languages.toList().sortedByDescending { it.second }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF222222), RoundedCornerShape(8.dp))
            .background(Color(0xFF0D1117))
            .padding(12.dp)
    ) {
        Text(
            text = "LANGUAGES",
            color = Color.Gray,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            fontFamily = FontFamily.Default,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF21262D))
        ) {
            sortedLanguages.forEach { (lang, bytes) ->
                val fraction = (bytes.toFloat() / totalBytes)
                if (fraction > 0.005f) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(fraction)
                            .background(getLanguageColor(lang))
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        val chunks = sortedLanguages.chunked(3)
        chunks.forEach { chunk ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                chunk.forEach { (lang, bytes) ->
                    val percentage = (bytes.toDouble() / totalBytes) * 100
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(getLanguageColor(lang))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = lang,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Default
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = String.format(java.util.Locale.US, "%.1f%%", percentage),
                            color = Color.Gray,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Default
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RepositoryDetailScreen(viewModel: GitHubViewModel, owner: String, name: String) {
    val loading by viewModel.repoDetailLoading.collectAsState()
    val repo by viewModel.repoDetailData.collectAsState()
    val isBookmarked by viewModel.repoDetailIsBookmarked.collectAsState()
    val error by viewModel.repoDetailError.collectAsState()

    val activeTab by viewModel.repoActiveTab.collectAsState()

    if (loading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFF00E5FF))
        }
        return
    }

    if (error != null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Error, contentDescription = null, tint = Color.Red, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text("Connection Failed", color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Text(error!!, color = Color.Gray, fontSize = 12.sp, fontFamily = FontFamily.Default)
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { viewModel.loadRepositoryDetails(owner, name) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color.Black)
            ) {
                Text("RETRY", fontFamily = FontFamily.Default)
            }
        }
        return
    }

    val currentRepo = repo ?: return

    val scrollKey = "repo_detail_${owner}_${name}"
    val savedValue = viewModel.getScrollState(scrollKey).first
    val scrollState = rememberScrollState(initial = savedValue)
    
    DisposableEffect(scrollState) {
        onDispose {
            viewModel.saveScrollState(scrollKey, scrollState.value, 0)
        }
    }

    val detailModifier = if (activeTab == "CODE" || activeTab == "RELEASES") {
        Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    } else {
        Modifier.fillMaxSize()
    }

    Column(modifier = detailModifier) {
        // Hero Repo Info Header Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .border(1.dp, Color(0xFF222222), RoundedCornerShape(8.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF060606))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentRepo.owner.login,
                            color = Color.Gray,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Default,
                            modifier = Modifier.clickable {
                                viewModel.navigateTo(Screen.UserProfile(currentRepo.owner.login))
                                viewModel.loadUserProfile(currentRepo.owner.login)
                            }
                        )
                        Text(
                            text = currentRepo.name,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp
                        )
                    }
                    IconButton(
                        onClick = { viewModel.toggleBookmark(currentRepo) }
                    ) {
                        Icon(
                            imageVector = if (isBookmarked) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Pin Repo",
                            tint = Color(0xFF00E5FF)
                        )
                    }
                }

                if (!currentRepo.description.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = currentRepo.description,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, contentDescription = "Stars", tint = Color(0xFF00E5FF), modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("${currentRepo.stargazersCount ?: 0}", color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Default)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ForkRight, contentDescription = "Forks", tint = Color.Gray, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("${currentRepo.forksCount ?: 0}", color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Default)
                        }
                    }

                    if (!currentRepo.language.isNullOrEmpty()) {
                        UnitBadge(label = currentRepo.language, color = Color(0xFF00E5FF))
                    }
                }
            }
        }

        val languages by viewModel.repoLanguages.collectAsState()
        val languagesLoading by viewModel.repoLanguagesLoading.collectAsState()

        if (languagesLoading) {
            Box(modifier = Modifier.fillMaxWidth().height(12.dp).padding(horizontal = 12.dp, vertical = 4.dp)) {
                LinearProgressIndicator(color = Color(0xFF00E5FF), modifier = Modifier.fillMaxWidth())
            }
        } else if (languages.isNotEmpty()) {
            RepositoryLanguagesBar(
                languages = languages,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }

        // Snap tab segments (Code, Issues, Commits, Releases) flipped instantly
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .border(1.dp, Color(0xFF1E1E1E), RoundedCornerShape(0.dp))
                .background(Color.Black),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val tabs = listOf("CODE", "ISSUES", "COMMITS", "RELEASES")
            tabs.forEach { tab ->
                val isSelected = activeTab == tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(if (isSelected) Color(0xFF00E5FF) else Color.Transparent)
                        .clickable {
                            viewModel.repoActiveTab.value = tab
                            if (tab == "ISSUES") viewModel.loadIssues("open")
                            if (tab == "COMMITS") viewModel.loadCommits()
                            if (tab == "RELEASES") viewModel.loadReleases()
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tab,
                        color = if (isSelected) Color.Black else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Default
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Tab views rendering instantly without transition structures
        when (activeTab) {
            "CODE" -> CodeContentsTab(viewModel)
            "ISSUES" -> IssuesTab(viewModel)
            "COMMITS" -> CommitsTab(viewModel)
            "RELEASES" -> ReleasesTab(viewModel)
        }
    }
}

sealed class MarkdownBlock {
    data class Header(val text: String, val level: Int) : MarkdownBlock()
    data class CodeBlock(val code: String, val language: String?) : MarkdownBlock()
    data class Bullet(val text: String) : MarkdownBlock()
    data class TaskList(val text: String, val checked: Boolean) : MarkdownBlock()
    data class NumberedList(val number: String, val text: String) : MarkdownBlock()
    data class Blockquote(val text: String) : MarkdownBlock()
    object HorizontalRule : MarkdownBlock()
    data class ImageBlock(val alt: String, val url: String, val linkUrl: String? = null) : MarkdownBlock()
    data class Paragraph(val text: String) : MarkdownBlock()
}
fun parseMarkdownBlocks(content: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val sanitizedContent = content.replace(SyntaxAndMarkdownConstants.commentRegex, "")
    
    // Pre-process HTML tags outside of code blocks
    val parts = sanitizedContent.split("```")
    val processedContent = parts.mapIndexed { index, part ->
        if (index % 2 == 0) { // Outside code block
            var active = part
            for (i in 1..6) {
                active = active.replace(SyntaxAndMarkdownConstants.header6Regexes[i - 1]) {
                    val hashes = "#".repeat(i)
                    "\n$hashes ${it.groupValues[1].trim()}\n"
                }
            }
            active = active.replace(SyntaxAndMarkdownConstants.boldStyleRegex, "**$1**")
            active = active.replace(SyntaxAndMarkdownConstants.italicStyleRegex, "*$1*")
            active = active.replace(SyntaxAndMarkdownConstants.codeStyleRegex, "`$1`")
            active = active.replace(SyntaxAndMarkdownConstants.kbdStyleRegex, "`$1`")
            
            // Robust HTML images extraction
            active = active.replace(SyntaxAndMarkdownConstants.imageHtmlRegex) { matchResult ->
                val src = matchResult.groupValues[1]
                val altMatch = SyntaxAndMarkdownConstants.altPropertyRegex.find(matchResult.value)
                val alt = altMatch?.groupValues?.get(1) ?: "Image"
                "![$alt]($src)"
            }
            
            // Robust HTML links extraction
            active = active.replace(SyntaxAndMarkdownConstants.anchorHtmlRegex) { matchResult ->
                val url = matchResult.groupValues[1]
                val content = matchResult.groupValues[2].replace("\n", " ").trim()
                "[$content]($url)"
            }
            
            // Interpret other helper skeleton tags
            active = active.replace(SyntaxAndMarkdownConstants.brTagRegex, "\n")
            active = active.replace(SyntaxAndMarkdownConstants.liTagRegex, "\n- ")
            active = active.replace(SyntaxAndMarkdownConstants.trTagRegex, "\n")
            active = active.replace(SyntaxAndMarkdownConstants.tdTagRegex, "  ")
            active = active.replace(SyntaxAndMarkdownConstants.thTagRegex, "  ")
            active = active.replace(SyntaxAndMarkdownConstants.pTagOpenRegex, "\n\n")
            active = active.replace(SyntaxAndMarkdownConstants.pTagCloseRegex, "\n\n")
            active = active.replace(SyntaxAndMarkdownConstants.divTagOpenRegex, "\n")
            active = active.replace(SyntaxAndMarkdownConstants.divTagCloseRegex, "\n")
            
            // Strike-down remaining tags cleanly
            active = active.replace(SyntaxAndMarkdownConstants.anyTagRegex, "")
            active
        } else {
            part
        }
    }.joinToString("```")

    val lines = processedContent.lines()
    var inCodeBlock = false
    val currentCodeLines = mutableListOf<String>()
    var codeLanguage: String? = null
    
    val currentParagraphLines = mutableListOf<String>()
    
    fun flushParagraph() {
        if (currentParagraphLines.isNotEmpty()) {
            val pText = currentParagraphLines.joinToString(" ").trim()
            if (pText.isNotBlank()) {
                blocks.add(MarkdownBlock.Paragraph(pText))
            }
            currentParagraphLines.clear()
        }
    }

    for (line in lines) {
        var activeLine = line
        val trimmed = activeLine.trim()
        
        if (trimmed.startsWith("```")) {
            flushParagraph()
            if (inCodeBlock) {
                blocks.add(MarkdownBlock.CodeBlock(currentCodeLines.joinToString("\n"), codeLanguage))
                currentCodeLines.clear()
                codeLanguage = null
                inCodeBlock = false
            } else {
                inCodeBlock = true
                codeLanguage = trimmed.substringAfter("```").trim().takeIf { it.isNotEmpty() }
            }
            continue
        }

        if (inCodeBlock) {
            currentCodeLines.add(line)
            continue
        }

        when {
            trimmed.isEmpty() -> {
                flushParagraph()
            }
            trimmed.startsWith("#") -> {
                flushParagraph()
                val level = trimmed.takeWhile { it == '#' }.length
                val text = trimmed.drop(level).trim()
                blocks.add(MarkdownBlock.Header(text, level))
            }
            trimmed.startsWith("- [x] ", ignoreCase = true) || trimmed.startsWith("* [x] ", ignoreCase = true) -> {
                flushParagraph()
                blocks.add(MarkdownBlock.TaskList(trimmed.substring(6).trim(), checked = true))
            }
            trimmed.startsWith("- [ ] ") || trimmed.startsWith("* [ ] ") -> {
                flushParagraph()
                blocks.add(MarkdownBlock.TaskList(trimmed.substring(6).trim(), checked = false))
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                flushParagraph()
                blocks.add(MarkdownBlock.Bullet(trimmed.substring(2).trim()))
            }
            trimmed.matches(SyntaxAndMarkdownConstants.numberedListRegex) -> {
                flushParagraph()
                val dotIndex = trimmed.indexOf('.')
                val num = trimmed.substring(0, dotIndex)
                val rest = trimmed.substring(dotIndex + 1).trim()
                blocks.add(MarkdownBlock.NumberedList(num, rest))
            }
            trimmed.startsWith(">") -> {
                flushParagraph()
                blocks.add(MarkdownBlock.Blockquote(trimmed.substring(1).trim()))
            }
            ((trimmed.startsWith("![") && trimmed.endsWith(")")) || (trimmed.startsWith("[![") && trimmed.endsWith(")"))) &&
            (trimmed.count { it == '[' } == 1 || (trimmed.startsWith("[![") && trimmed.count { it == '[' } == 2)) -> {
                flushParagraph()
                
                val linkedMatch = SyntaxAndMarkdownConstants.linkedImgRegex.find(trimmed)
                val match = SyntaxAndMarkdownConstants.imgOnlyRegex.find(trimmed)
                
                if (linkedMatch != null) {
                    val alt = linkedMatch.groupValues[1]
                    val imgUrl = linkedMatch.groupValues[2].substringBefore(" ")
                    val linkUrl = linkedMatch.groupValues[3].substringBefore(" ")
                    blocks.add(MarkdownBlock.ImageBlock(alt, imgUrl, linkUrl))
                } else if (match != null) {
                    val alt = match.groupValues[1]
                    val imgUrl = match.groupValues[2].substringBefore(" ")
                    blocks.add(MarkdownBlock.ImageBlock(alt, imgUrl, null))
                } else {
                    currentParagraphLines.add(activeLine)
                }
            }
            (trimmed == "---" || trimmed == "***" || trimmed == "___") -> {
                flushParagraph()
                blocks.add(MarkdownBlock.HorizontalRule)
            }
            else -> {
                currentParagraphLines.add(activeLine)
            }
        }
    }
    flushParagraph()
    if (inCodeBlock && currentCodeLines.isNotEmpty()) {
        blocks.add(MarkdownBlock.CodeBlock(currentCodeLines.joinToString("\n"), codeLanguage))
    }
    return blocks
}

fun unescapeHtml(text: String): String {
    return text
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&nbsp;", " ")
}

data class ParsedMarkdownText(
    val annotatedString: AnnotatedString,
    val inlineContent: Map<String, InlineTextContent>
)

fun getInlineImagePlaceholder(imageUrl: String): Placeholder {
    val isBadge = imageUrl.contains("shields.io", ignoreCase = true) ||
            imageUrl.contains("badge", ignoreCase = true) ||
            imageUrl.contains("licence", ignoreCase = true) ||
            imageUrl.contains("license", ignoreCase = true) ||
            imageUrl.contains("logo", ignoreCase = true) ||
            imageUrl.contains("button", ignoreCase = true) ||
            imageUrl.contains("counter", ignoreCase = true) ||
            imageUrl.contains("npm", ignoreCase = true) ||
            imageUrl.contains("v/", ignoreCase = true)
    
    val width = if (isBadge) 95.sp else 135.sp
    val height = 22.sp
    return Placeholder(
        width = width,
        height = height,
        placeholderVerticalAlign = PlaceholderVerticalAlign.Center
    )
}

fun parseInlineMarkdown(rawText: String, baseUrl: String? = null): ParsedMarkdownText {
    var text = rawText
        .replace(SyntaxAndMarkdownConstants.commentRegex, "")
        .replace(SyntaxAndMarkdownConstants.brTagRegex, "\n")
        .replace(SyntaxAndMarkdownConstants.inlineBoldStrongRegex, "**$1**")
        .replace(SyntaxAndMarkdownConstants.inlineItalicEmRegex, "*$1*")
        .replace(SyntaxAndMarkdownConstants.inlineCodeRegexSoft, "`$1`")
        .replace(SyntaxAndMarkdownConstants.inlineKbdStyleRegexSoft, "`$1`")
        
    // Transform inline HTML images and anchors first to avoid stripping them with other tags
    text = text.replace(SyntaxAndMarkdownConstants.imageHtmlRegex) { matchResult ->
        val src = matchResult.groupValues[1]
        val altMatch = SyntaxAndMarkdownConstants.altPropertyRegex.find(matchResult.value)
        val alt = altMatch?.groupValues?.get(1) ?: "Image"
        "![$alt]($src)"
    }
    
    text = text.replace(SyntaxAndMarkdownConstants.anchorHtmlRegex) { matchResult ->
        val url = matchResult.groupValues[1]
        val content = matchResult.groupValues[2].replace("\n", " ").trim()
        "[$content]($url)"
    }
    text = text.replace(SyntaxAndMarkdownConstants.anyTagRegex, "")
    
    text = unescapeHtml(text)

    val builder = AnnotatedString.Builder()
    val inlineContentMap = mutableMapOf<String, InlineTextContent>()
    var index = 0
    while (index < text.length) {
        val nextTick = text.indexOf('`', index)
        val nextStarStar = text.indexOf("**", index)
        val nextStar = text.indexOf('*', index)
        val nextLinkedImg = text.indexOf("[![", index)
        val nextImg = text.indexOf("![", index)
        val nextBracket = text.indexOf('[', index)
        
        val candidates = mutableListOf<Pair<Int, String>>()
        if (nextTick != -1) candidates.add(nextTick to "tick")
        if (nextStarStar != -1) candidates.add(nextStarStar to "starstar")
        if (nextStar != -1 && nextStar != nextStarStar && nextStar != nextStarStar + 1) {
            candidates.add(nextStar to "star")
        }
        if (nextLinkedImg != -1) {
            candidates.add(nextLinkedImg to "linked_img")
        }
        if (nextImg != -1 && (nextLinkedImg == -1 || nextImg != nextLinkedImg + 1)) {
            candidates.add(nextImg to "img")
        }
        if (nextBracket != -1 && 
            (nextImg == -1 || nextBracket != nextImg + 1) && 
            (nextLinkedImg == -1 || nextBracket != nextLinkedImg)) {
            candidates.add(nextBracket to "link")
        }
        
        val first = candidates.minByOrNull { it.first }
        
        if (first == null) {
            builder.append(text.substring(index))
            break
        }
        
        val firstSpecial = first.first
        val markerType = first.second
        
        if (firstSpecial > index) {
            builder.append(text.substring(index, firstSpecial))
        }
        
        when (markerType) {
            "tick" -> {
                val secondTick = text.indexOf('`', firstSpecial + 1)
                if (secondTick != -1) {
                    builder.pushStyle(
                        SpanStyle(
                            background = Color(0xFF21262D),
                            color = Color(0xFFFF79C6),
                            fontFamily = FontFamily.Default,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    builder.append(" " + text.substring(firstSpecial + 1, secondTick) + " ")
                    builder.pop()
                    index = secondTick + 1
                } else {
                    builder.append("`")
                    index = firstSpecial + 1
                }
            }
            "starstar" -> {
                val secondStarStar = text.indexOf("**", firstSpecial + 2)
                if (secondStarStar != -1) {
                    builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color.White))
                    builder.append(text.substring(firstSpecial + 2, secondStarStar))
                    builder.pop()
                    index = secondStarStar + 2
                } else {
                    builder.append("**")
                    index = firstSpecial + 2
                }
            }
            "star" -> {
                val secondStar = text.indexOf('*', firstSpecial + 1)
                if (secondStar != -1) {
                    builder.pushStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = Color.LightGray))
                    builder.append(text.substring(firstSpecial + 1, secondStar))
                    builder.pop()
                    index = secondStar + 1
                } else {
                    builder.append("*")
                    index = firstSpecial + 1
                }
            }
            "linked_img" -> {
                val junction = text.indexOf(")](", firstSpecial + 3)
                if (junction != -1) {
                    val altEnd = text.indexOf("](", firstSpecial + 3)
                    if (altEnd != -1 && altEnd < junction) {
                        val altText = text.substring(firstSpecial + 3, altEnd)
                        val originalImgUrl = text.substring(altEnd + 2, junction)
                        val imageUrl = resolveMarkdownUrl(originalImgUrl, baseUrl)
                        
                        val closingParen = text.indexOf(')', junction + 3)
                        if (closingParen != -1) {
                            val originalLinkUrl = text.substring(junction + 3, closingParen)
                            val linkUrl = resolveMarkdownUrl(originalLinkUrl, baseUrl)
                            
                            val imgId = "inline_linked_img_${java.util.UUID.randomUUID()}"
                            val placeholder = getInlineImagePlaceholder(imageUrl)
                            
                            inlineContentMap[imgId] = InlineTextContent(placeholder) {
                                val context = androidx.compose.ui.platform.LocalContext.current
                                val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                                val vm = LocalGitHubViewModel.current
                                val requestBuilder = coil.request.ImageRequest.Builder(context)
                                    .data(imageUrl)
                                    .crossfade(false)
                                if (imageUrl.contains(".svg", ignoreCase = true) || imageUrl.contains("svg", ignoreCase = true)) {
                                    requestBuilder.decoderFactory(coil.decode.SvgDecoder.Factory())
                                }
                                val request = requestBuilder.build()
                                AsyncImage(
                                    model = request,
                                    contentDescription = altText,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable {
                                            try {
                                                val handled = if (vm != null) handleGitHubUrl(linkUrl, vm) else false
                                                if (!handled) {
                                                    uriHandler.openUri(linkUrl)
                                                }
                                            } catch (e: Exception) {}
                                        },
                                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                )
                            }
                            
                            builder.appendInlineContent(imgId, "[Image]")
                            index = closingParen + 1
                        } else {
                            builder.append("[![")
                            index = firstSpecial + 3
                        }
                    } else {
                        builder.append("[![")
                        index = firstSpecial + 3
                    }
                } else {
                    builder.append("[![")
                    index = firstSpecial + 3
                }
            }
            "img" -> {
                val closingBracket = text.indexOf(']', firstSpecial + 2)
                if (closingBracket != -1 && closingBracket + 1 < text.length && text[closingBracket + 1] == '(') {
                    val closingParen = text.indexOf(')', closingBracket + 2)
                    if (closingParen != -1) {
                        val altText = text.substring(firstSpecial + 2, closingBracket)
                        val originalUrl = text.substring(closingBracket + 2, closingParen)
                        val imageUrl = resolveMarkdownUrl(originalUrl, baseUrl)
                        
                        val imgId = "inline_img_${java.util.UUID.randomUUID()}"
                        val placeholder = getInlineImagePlaceholder(imageUrl)
                        
                        inlineContentMap[imgId] = InlineTextContent(placeholder) {
                            val context = androidx.compose.ui.platform.LocalContext.current
                            val requestBuilder = coil.request.ImageRequest.Builder(context)
                                .data(imageUrl)
                                .crossfade(false)
                            if (imageUrl.contains(".svg", ignoreCase = true) || imageUrl.contains("svg", ignoreCase = true)) {
                                requestBuilder.decoderFactory(coil.decode.SvgDecoder.Factory())
                            }
                            val request = requestBuilder.build()
                            AsyncImage(
                                model = request,
                                contentDescription = altText,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Fit
                            )
                        }
                        
                        builder.appendInlineContent(imgId, "[Image]")
                        index = closingParen + 1
                    } else {
                        builder.append("![")
                        index = firstSpecial + 2
                    }
                } else {
                    builder.append("![")
                    index = firstSpecial + 2
                }
            }
            "link" -> {
                val closingBracket = text.indexOf(']', firstSpecial + 1)
                if (closingBracket != -1 && closingBracket + 1 < text.length && text[closingBracket + 1] == '(') {
                    val closingParen = text.indexOf(')', closingBracket + 2)
                    if (closingParen != -1) {
                        val linkText = text.substring(firstSpecial + 1, closingBracket)
                        val originalUrl = text.substring(closingBracket + 2, closingParen)
                        val url = resolveMarkdownUrl(originalUrl, baseUrl)
                        
                        builder.pushStringAnnotation(tag = "URL", annotation = url)
                        builder.pushStyle(
                            SpanStyle(
                                color = Color(0xFF58A6FF),
                                fontWeight = FontWeight.SemiBold,
                                textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                            )
                        )
                        builder.append(linkText)
                        builder.pop()
                        builder.pop()
                        
                        index = closingParen + 1
                    } else {
                        builder.append("[")
                        index = firstSpecial + 1
                    }
                } else {
                    builder.append("[")
                    index = firstSpecial + 1
                }
            }
        }
    }
    return ParsedMarkdownText(builder.toAnnotatedString(), inlineContentMap)
}

@Composable
fun InteractiveMarkdownText(
    annotatedString: AnnotatedString,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFC9D1D9),
    fontSize: TextUnit = 14.sp,
    fontWeight: FontWeight? = null,
    fontFamily: androidx.compose.ui.text.font.FontFamily? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    inlineContent: Map<String, InlineTextContent> = emptyMap()
) {
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val vm = LocalGitHubViewModel.current
    val layoutResult = remember { mutableStateOf<TextLayoutResult?>(null) }
    
    Text(
        text = annotatedString,
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        lineHeight = lineHeight,
        inlineContent = inlineContent,
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures { offset ->
                layoutResult.value?.let { textLayoutResult ->
                    val position = textLayoutResult.getOffsetForPosition(offset)
                    annotatedString.getStringAnnotations(tag = "URL", start = position, end = position)
                        .firstOrNull()?.let { annotation ->
                            try {
                                val url = annotation.item
                                val handled = if (vm != null) handleGitHubUrl(url, vm) else false
                                if (!handled) {
                                    uriHandler.openUri(url)
                                }
                            } catch (e: Exception) {
                                // Ignore
                            }
                        }
                }
            }
        },
        onTextLayout = { layoutResult.value = it }
    )
}

@Composable
fun MarkdownText(
    text: String,
    baseUrl: String? = null,
    searchQuery: String = "",
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFC9D1D9),
    fontSize: TextUnit = 14.sp,
    fontWeight: FontWeight? = null,
    fontFamily: androidx.compose.ui.text.font.FontFamily? = null,
    lineHeight: TextUnit = TextUnit.Unspecified
) {
    val parsed = remember(text, baseUrl) { parseInlineMarkdown(text, baseUrl) }
    val annotatedString = if (searchQuery.isNotBlank()) {
        applySearchHighlight(parsed.annotatedString, searchQuery)
    } else {
        parsed.annotatedString
    }
    InteractiveMarkdownText(
        annotatedString = annotatedString,
        inlineContent = parsed.inlineContent,
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        lineHeight = lineHeight
    )
}

fun resolveMarkdownUrl(url: String, baseUrl: String?): String {
    var cleanUrl = url.trim().removePrefix("./")
    if (cleanUrl.contains("github.com") && cleanUrl.contains("/blob/")) {
        cleanUrl = cleanUrl
            .replace("github.com", "raw.githubusercontent.com")
            .replace("/blob/", "/")
    }
    if (cleanUrl.startsWith("http://") || cleanUrl.startsWith("https://") || cleanUrl.startsWith("data:")) {
        return cleanUrl
    }
    if (baseUrl.isNullOrEmpty()) {
        return cleanUrl
    }
    return if (cleanUrl.startsWith("/")) {
        val parts = baseUrl.split("/")
        if (parts.size >= 6) {
            val root = parts.take(6).joinToString("/")
            "$root$cleanUrl"
        } else {
            "$baseUrl$cleanUrl"
        }
    } else {
        "$baseUrl$cleanUrl"
    }
}

@Composable
fun MarkdownPreview(readme: String, baseUrl: String? = null, imageQuality: String = "HIGH", searchQuery: String = "") {
    val blocksState = produceState<List<MarkdownBlock>>(initialValue = emptyList(), key1 = readme) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            parseMarkdownBlocks(readme)
        }
    }
    val blocks = blocksState.value
    
    if (blocks.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier = Modifier.size(24.dp)
            )
        }
    } else {
        Column(modifier = Modifier.fillMaxWidth()) {
            blocks.forEach { block ->
                when (block) {
                is MarkdownBlock.Header -> {
                    MarkdownText(
                        text = block.text,
                        baseUrl = baseUrl,
                        searchQuery = searchQuery,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = when (block.level) {
                            1 -> 24.sp
                            2 -> 20.sp
                            3 -> 18.sp
                            else -> 16.sp
                        },
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                    )
                }
                is MarkdownBlock.Paragraph -> {
                    MarkdownText(
                        text = block.text,
                        baseUrl = baseUrl,
                        searchQuery = searchQuery,
                        color = Color(0xFFC9D1D9),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 8.dp),
                        lineHeight = 20.sp
                    )
                }
                is MarkdownBlock.CodeBlock -> {
                    val codeAnnotated = if (searchQuery.isNotBlank()) {
                        highlightSearchAnnotated(block.code, searchQuery)
                    } else {
                        AnnotatedString(block.code)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .background(Color(0xFF161B22), RoundedCornerShape(6.dp))
                            .border(1.dp, Color(0xFF30363D), RoundedCornerShape(6.dp))
                            .horizontalScroll(rememberScrollState())
                            .padding(12.dp)
                    ) {
                        SelectionContainer {
                            Text(
                                text = codeAnnotated,
                                color = Color(0xFFE6EDF3),
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Default,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
                is MarkdownBlock.Bullet -> {
                    Row(modifier = Modifier.padding(bottom = 4.dp, start = 8.dp)) {
                        Text("•", color = Color.Gray, modifier = Modifier.padding(end = 8.dp))
                        MarkdownText(
                            text = block.text,
                            baseUrl = baseUrl,
                            searchQuery = searchQuery,
                            color = Color(0xFFC9D1D9),
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                    }
                }
                is MarkdownBlock.NumberedList -> {
                    Row(modifier = Modifier.padding(bottom = 4.dp, start = 8.dp)) {
                        Text("${block.number}.", color = Color.Gray, modifier = Modifier.padding(end = 8.dp))
                        MarkdownText(
                            text = block.text,
                            baseUrl = baseUrl,
                            searchQuery = searchQuery,
                            color = Color(0xFFC9D1D9),
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                    }
                }
                is MarkdownBlock.TaskList -> {
                    Row(modifier = Modifier.padding(bottom = 4.dp, start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (block.checked) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (block.checked) Color(0xFF238636) else Color.Gray,
                            modifier = Modifier.size(16.dp).padding(end = 6.dp)
                        )
                        MarkdownText(
                            text = block.text,
                            baseUrl = baseUrl,
                            color = Color(0xFFC9D1D9),
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                    }
                }
                is MarkdownBlock.Blockquote -> {
                    Row(modifier = Modifier.padding(start = 8.dp, bottom = 8.dp, top = 4.dp)) {
                        Box(modifier = Modifier.width(4.dp).height(IntrinsicSize.Min).background(Color(0xFF30363D)))
                        MarkdownText(
                            text = block.text,
                            baseUrl = baseUrl,
                            color = Color(0xFF8B949E),
                            fontSize = 14.sp,
                            modifier = Modifier.padding(start = 12.dp),
                            lineHeight = 20.sp
                        )
                    }
                }
                is MarkdownBlock.HorizontalRule -> {
                    HorizontalDivider(color = Color(0xFF30363D), thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))
                }
                is MarkdownBlock.ImageBlock -> {
                    if (imageQuality != "DISABLE" && imageQuality != "OFF") {
                        val imageUrl = resolveMarkdownUrl(block.url, baseUrl)
                        val context = androidx.compose.ui.platform.LocalContext.current
                        val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                        val vm = LocalGitHubViewModel.current
                        val request = coil.request.ImageRequest.Builder(context)
                            .data(imageUrl)
                            .crossfade(false)
                        if (imageUrl.contains(".svg", ignoreCase = true) || imageUrl.contains("svg", ignoreCase = true)) {
                            request.decoderFactory(coil.decode.SvgDecoder.Factory())
                        }
                        request.memoryCacheKey("${imageUrl}_cached")
                        request.diskCacheKey("${imageUrl}_cached")
                        var modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                        if (block.linkUrl != null) {
                            modifier = modifier.clickable {
                                try {
                                    val handled = if (vm != null) handleGitHubUrl(block.linkUrl, vm) else false
                                    if (!handled) {
                                        uriHandler.openUri(block.linkUrl)
                                    }
                                } catch (e: Exception) {}
                            }
                        }
                        AsyncImage(
                            model = request.build(),
                            contentDescription = block.alt,
                            modifier = modifier,
                            contentScale = androidx.compose.ui.layout.ContentScale.FillWidth
                        )
                    }
                }
            }
        }
    }
    }
}

@Composable
fun CodeContentsTab(viewModel: GitHubViewModel) {
    val loading by viewModel.codeContentsLoading.collectAsState()
    val contents by viewModel.codeContents.collectAsState()
    val codePath by viewModel.codePath.collectAsState()
    val error by viewModel.codeError.collectAsState()

    val readmeNameState by viewModel.readmeName.collectAsState()
    val readmeLoadingState by viewModel.readmeLoading.collectAsState()
    val readmeContentState by viewModel.readmeContent.collectAsState()
    val readmeDownloadUrl by viewModel.readmeDownloadUrl.collectAsState()
    val markdownImageQuality by viewModel.markdownImageQuality.collectAsState()

    if (loading) {
        Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFF00E5FF))
        }
        return
    }

    if (error != null) {
        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            Text("No files or Access restricted", color = Color.Gray, fontSize = 13.sp, fontFamily = FontFamily.Default)
        }
        return
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        // Path indicator with breadcrumbs returning capability
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF1A1A1A), RoundedCornerShape(4.dp))
                .background(Color(0xFF050505))
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (codePath.isEmpty()) "root" else codePath,
                color = Color.White,
                fontSize = 12.sp,
                fontFamily = FontFamily.Default,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            val isAtRoot = viewModel.isAtRootDirectory()
            if (!isAtRoot) {
                IconButton(
                    modifier = Modifier.size(20.dp),
                    onClick = { viewModel.navigateUpDirectory() }
                ) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = "Up", tint = Color(0xFF00E5FF), modifier = Modifier.size(14.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Files listing scrollable section
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .border(1.dp, Color(0xFF1E1E1E), RoundedCornerShape(6.dp))
                .background(Color.Black)
        ) {
            if (contents.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Directory is Empty", color = Color.Gray, fontSize = 12.sp, fontFamily = FontFamily.Default)
                }
            } else {
                val repoOwner = viewModel.repoDetailData.value?.owner?.login ?: ""
                val repoName = viewModel.repoDetailData.value?.name ?: ""
                val dirScrollKey = "dir_${repoOwner}_${repoName}_${codePath}"
                val dirSavedState = viewModel.getScrollState(dirScrollKey)
                val dirListState = rememberLazyListState()
                
                // Restore directory scroll after contents load
                LaunchedEffect(dirListState, contents, codePath) {
                    if (contents.isNotEmpty()) {
                        val targetIndex = dirSavedState.first.coerceAtLeast(0)
                        val targetOffset = dirSavedState.second.coerceAtLeast(0)
                        if (targetIndex > 0 || targetOffset > 0) {
                            dirListState.scrollToItem(targetIndex, targetOffset)
                        }
                    }
                }
                
                DisposableEffect(dirListState, codePath) {
                    onDispose {
                        viewModel.saveScrollState(dirScrollKey, dirListState.firstVisibleItemIndex, dirListState.firstVisibleItemScrollOffset)
                    }
                }

                LazyColumn(
                    state = dirListState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(8.dp)
                ) {
                    items(contents, key = { it.path }) { file ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFF101010), RoundedCornerShape(4.dp))
                                .background(Color(0xFF030303))
                                .clickable {
                                    if (file.type == "dir") {
                                        viewModel.navigateIntoDirectory(file.path)
                                    } else {
                                        viewModel.navigateTo(
                                            Screen.FileView(
                                                owner = viewModel.repoDetailData.value?.owner?.login ?: "",
                                                repo = viewModel.repoDetailData.value?.name ?: "",
                                                filePath = file.path,
                                                downloadUrl = file.downloadUrl ?: ""
                                            )
                                        )
                                    }
                                }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (file.type == "dir") Icons.Default.Folder else Icons.Default.Description,
                                contentDescription = null,
                                tint = if (file.type == "dir") Color(0xFFBD93F9) else Color(0xFF8BE9FD),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = file.name,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Default,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // README section flowing freely with the whole window
        if (readmeNameState != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF333333), RoundedCornerShape(8.dp))
                    .background(Color(0xFF0D1117))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF161B22))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.MenuBook,
                        contentDescription = "README",
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = readmeNameState!!,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Default,
                        fontSize = 13.sp
                    )
                }

                HorizontalDivider(color = Color(0xFF30363D), thickness = 1.dp)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    if (readmeLoadingState) {
                        Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color(0xFF00E5FF))
                        }
                    } else if (readmeContentState != null) {
                            val baseUrl = readmeDownloadUrl?.substringBeforeLast("/")?.plus("/")
                            MarkdownPreview(readme = readmeContentState!!, baseUrl = baseUrl, imageQuality = markdownImageQuality)
                    }
                }
            }
        }
    }
}

@Composable
fun IssuesTab(viewModel: GitHubViewModel) {
    val loading by viewModel.issuesLoading.collectAsState()
    val issues by viewModel.issuesList.collectAsState()
    val state by viewModel.issuesState.collectAsState()
    val error by viewModel.issuesError.collectAsState()

    var activeIssueData by remember { mutableStateOf<GitHubIssue?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        // Toggle bar for OPEN / CLOSED
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Button(
                modifier = Modifier.weight(1f),
                onClick = { viewModel.loadIssues("open") },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state == "open") Color(0xFF00E5FF) else Color(0xFF111111),
                    contentColor = if (state == "open") Color.Black else Color.White
                ),
                shape = RoundedCornerShape(0.dp)
            ) {
                Text("OPEN ISSUES", fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.Default)
            }
            Button(
                modifier = Modifier.weight(1f),
                onClick = { viewModel.loadIssues("closed") },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state == "closed") Color(0xFF00E5FF) else Color(0xFF111111),
                    contentColor = if (state == "closed") Color.Black else Color.White
                ),
                shape = RoundedCornerShape(0.dp)
            ) {
                Text("CLOSED ISSUES", fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.Default)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (loading) {
            Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF00E5FF))
            }
            return
        }

        if (error != null) {
            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                Text("Error: ${error}", color = Color.Gray, fontSize = 12.sp, fontFamily = FontFamily.Default)
            }
            return
        }

        if (issues.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("No matching issues found", color = Color.Gray, fontSize = 13.sp, fontFamily = FontFamily.Default)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(issues, key = { it.id }) { issue ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFF1A1A1A), RoundedCornerShape(4.dp))
                            .clickable { activeIssueData = issue },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF020202))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "#${issue.number}",
                                    color = Color(0xFF00E5FF),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Default
                                )
                                Text(
                                    text = issue.createdAt?.take(10) ?: "",
                                    color = Color.Gray,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Default
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = issue.title,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AccountCircle, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(issue.user.login, color = Color.Gray, fontSize = 11.sp, fontFamily = FontFamily.Default)
                                }
                                if (issue.comments ?: 0 > 0) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Comment, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("${issue.comments}", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Default)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal popup representing specific issue commentary
    activeIssueData?.let { issue ->
        Dialog(onDismissRequest = { activeIssueData = null }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(2.dp, Color(0xFF00E5FF), RoundedCornerShape(8.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.Black)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .heightIn(max = 500.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "#${issue.number} // ${issue.state.uppercase()}",
                            color = Color(0xFF00E5FF),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Default
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = issue.title,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Opened by ${issue.user.login}",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Default
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(color = Color(0xFF222222))
                    Spacer(modifier = Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .border(1.dp, Color(0xFF111111), RoundedCornerShape(4.dp))
                            .background(Color(0xFF030303))
                            .padding(8.dp)
                    ) {
                        LazyColumn {
                            item {
                                Text(
                                    text = if (issue.body.isNullOrBlank()) "[No Description provided]" else issue.body,
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        modifier = Modifier.align(Alignment.End),
                        onClick = { activeIssueData = null },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color.Black),
                        shape = RoundedCornerShape(0.dp)
                    ) {
                        Text("BACK", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Default, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun CommitDiffView(file: GitHubCommitFile) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .border(1.dp, Color(0xFF333333), RoundedCornerShape(6.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0D1117))
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF161B22))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    val statusColor = when (file.status) {
                        "added" -> Color(0xFF3FB950)
                        "removed" -> Color(0xFFF85149)
                        else -> Color(0xFFE3B341)
                    }
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = file.filename,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Default,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (file.additions > 0) {
                        Text("+${file.additions}", color = Color(0xFF3FB950), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    if (file.deletions > 0) {
                        Text("-${file.deletions}", color = Color(0xFFF85149), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            HorizontalDivider(color = Color(0xFF30363D), thickness = 1.dp)

            val patch = file.patch
            if (patch.isNullOrEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No visual changes (binary or large asset file)", color = Color.Gray, fontSize = 11.sp)
                }
            } else {
                val patchLines = remember(patch) { patch.lines() }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF010409))
                        .padding(8.dp)
                ) {
                    patchLines.forEach { line ->
                        val (backgroundColor, textColor) = when {
                            line.startsWith("+") -> Color(0xFF14301B) to Color(0xFF4AC26A)
                            line.startsWith("-") -> Color(0xFF351515) to Color(0xFFFF6B6B)
                            line.startsWith("@@") -> Color(0xFF111E2E) to Color(0xFF58A6FF)
                            else -> Color.Transparent to Color(0xFFC9D1D9)
                        }
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(backgroundColor)
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = line,
                                color = textColor,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Default,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CommitsTab(viewModel: GitHubViewModel) {
    val loading by viewModel.commitsLoading.collectAsState()
    val commits by viewModel.commitsList.collectAsState()
    val error by viewModel.commitsError.collectAsState()

    val detailedCommitState by viewModel.activeCommitDetail.collectAsState()
    val detailedCommitLoading by viewModel.commitDetailLoading.collectAsState()
    val detailedCommitError by viewModel.commitDetailError.collectAsState()

    var activeCommitDetail by remember { mutableStateOf<GitHubCommit?>(null) }

    LaunchedEffect(activeCommitDetail) {
        val detail = activeCommitDetail
        if (detail != null) {
            val repoDetails = viewModel.repoDetailData.value
            if (repoDetails != null) {
                viewModel.loadCommitDetail(repoDetails.owner.login, repoDetails.name, detail.sha)
            }
        } else {
            viewModel.closeCommitDetail()
        }
    }

    if (loading) {
        Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFF00E5FF))
        }
        return
    }

    if (error != null) {
        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            Text("Error loading commits: ${error}", color = Color.Gray, fontSize = 12.sp, fontFamily = FontFamily.Default)
        }
        return
    }

    if (commits.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("No commits recorded", color = Color.Gray, fontSize = 13.sp, fontFamily = FontFamily.Default)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(commits, key = { item -> item.sha }) { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFF1A1A1A), RoundedCornerShape(4.dp))
                        .clickable { activeCommitDetail = item },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF030303))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.sha.take(7),
                                color = Color(0xFF00E5FF),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Default
                            )
                            Text(
                                text = item.commit.author.date.take(10),
                                color = Color.Gray,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Default
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = item.commit.message,
                            color = Color.White,
                            fontSize = 13.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = item.commit.author.name,
                                color = Color.Gray,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Default
                            )
                        }
                    }
                }
            }
        }
    }

    // Commits inspection pop-up specs dialog with live diffs
    activeCommitDetail?.let { commit ->
        Dialog(onDismissRequest = { activeCommitDetail = null }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    .border(2.dp, Color(0xFF00E5FF), RoundedCornerShape(8.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.Black)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Code, contentDescription = null, tint = Color(0xFF00E5FF))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("COMMIT DETAIL", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Default)
                        }
                        IconButton(onClick = { activeCommitDetail = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                Text("SHA HASH:", color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Default)
                                Text(commit.sha, color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold)

                                Spacer(modifier = Modifier.height(8.dp))
                                Text("AUTHOR:", color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Default)
                                Text("${commit.commit.author.name} <${commit.commit.author.email}>", color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Default)

                                Spacer(modifier = Modifier.height(8.dp))
                                Text("TIMESTAMP/DATE:", color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Default)
                                Text(commit.commit.author.date, color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Default)

                                Spacer(modifier = Modifier.height(8.dp))
                                Text("FULL MESSAGE:", color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Default)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF080808))
                                        .border(1.dp, Color(0xFF151515))
                                        .padding(8.dp)
                                ) {
                                    Text(
                                        text = commit.commit.message,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Default
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                Text("FILES CHANGED & CODE DIFFS:", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.Default)
                                Spacer(modifier = Modifier.height(4.dp))
                            }

                            if (detailedCommitLoading) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(100.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(color = Color(0xFF00E5FF))
                                    }
                                }
                            } else if (detailedCommitError != null) {
                                item {
                                    Text(
                                        text = "Failed to load code diffs: $detailedCommitError",
                                        color = Color.Red,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Default
                                    )
                                }
                            } else if (detailedCommitState?.files != null) {
                                val filesList = detailedCommitState!!.files!!
                                if (filesList.isEmpty()) {
                                    item {
                                        Text("No files were modified in this commit.", color = Color.Gray, fontSize = 11.sp, fontFamily = FontFamily.Default)
                                    }
                                } else {
                                    items(filesList) { file ->
                                        CommitDiffView(file)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ReleasesTab(viewModel: GitHubViewModel) {
    val loading by viewModel.releasesLoading.collectAsState()
    val releases by viewModel.releasesList.collectAsState()
    val error by viewModel.releasesError.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var releaseSearchQuery by remember { mutableStateOf("") }
    val repoDetail by viewModel.repoDetailData.collectAsState()

    // Release notes popup state
    var viewingReleaseNotes by remember { mutableStateOf<GitHubRelease?>(null) }
    val releaseNotesLoading by viewModel.releaseNotesLoading.collectAsState()
    val releaseNotesContent by viewModel.releaseNotesContent.collectAsState()

    if (loading) {
        Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFF00E5FF))
        }
        return
    }

    if (error != null) {
        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            Text("Error fetching releases", color = Color.Gray, fontSize = 12.sp, fontFamily = FontFamily.Default)
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (releases.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("No releases available", color = Color.Gray, fontSize = 12.sp, fontFamily = FontFamily.Default)
            }
        } else {
            // Custom compact search box for releases
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .border(
                        width = 1.dp,
                        color = if (releaseSearchQuery.isNotEmpty()) Color(0xFF00E5FF) else Color(0xFF30363D),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .background(Color(0xFF090D13))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = releaseSearchQuery,
                    onValueChange = { releaseSearchQuery = it },
                    modifier = Modifier.fillMaxSize(),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 12.sp,
                        color = Color.White
                    ),
                    singleLine = true,
                    decorationBox = { innerTextField ->
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                if (releaseSearchQuery.isEmpty()) {
                                    Text(
                                        "Search releases...",
                                        color = Color(0xFF8B949E),
                                        fontSize = 12.sp
                                    )
                                }
                                innerTextField()
                            }
                            if (releaseSearchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { releaseSearchQuery = "" },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = "Clear",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Filtered and sorted releases
            val filteredReleases = remember(releases, releaseSearchQuery) {
                if (releaseSearchQuery.isBlank()) {
                    releases
                } else {
                    val query = releaseSearchQuery.trim().lowercase()
                    releases.filter { release ->
                        (release.name?.lowercase()?.contains(query) == true) ||
                        release.tagName.lowercase().contains(query) ||
                        (release.body?.lowercase()?.contains(query) == true)
                    }.sortedByDescending { release ->
                        var score = 0
                        if (release.name?.lowercase()?.contains(query) == true) score += 3
                        if (release.tagName.lowercase().contains(query)) score += 2
                        if (release.body?.lowercase()?.contains(query) == true) score += 1
                        score
                    }
                }
            }

            if (filteredReleases.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("No releases match your search", color = Color.Gray, fontSize = 12.sp, fontFamily = FontFamily.Default)
                }
            } else {
                filteredReleases.forEach { release ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFF1F1F1F), RoundedCornerShape(4.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF020202))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (releaseSearchQuery.isNotBlank())
                                        highlightSearchAnnotated(release.name ?: release.tagName, releaseSearchQuery)
                                    else
                                        AnnotatedString(release.name ?: release.tagName),
                                    color = Color(0xFF00E5FF),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Default,
                                    modifier = Modifier.weight(1f)
                                )
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFF111111), RoundedCornerShape(2.dp))
                                        .border(1.dp, Color(0xFF333333), RoundedCornerShape(2.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (releaseSearchQuery.isNotBlank())
                                            highlightSearchAnnotated(release.tagName, releaseSearchQuery)
                                        else
                                            AnnotatedString(release.tagName),
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Default
                                    )
                                }
                            }
                            if (!release.body.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (releaseSearchQuery.isNotBlank())
                                        highlightSearchAnnotated(release.body ?: "", releaseSearchQuery)
                                    else
                                        AnnotatedString(release.body ?: ""),
                                    color = Color.LightGray,
                                    fontSize = 11.sp,
                                    maxLines = 4,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewingReleaseNotes = release
                                            viewModel.scrapeReleaseNotes(release.htmlUrl ?: return@clickable)
                                        }
                                )
                            } else if (release.htmlUrl != null) {
                                // No body from API — still allow click to scrape from web page
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "View release notes...",
                                    color = Color(0xFF00E5FF),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Default,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewingReleaseNotes = release
                                            viewModel.scrapeReleaseNotes(release.htmlUrl)
                                        }
                                )
                            }
                            
                            val assetsCount = release.assets.size + (if (!release.zipballUrl.isNullOrBlank()) 1 else 0) + (if (!release.tarballUrl.isNullOrBlank()) 1 else 0)
                            if (assetsCount > 0) {
                                Spacer(modifier = Modifier.height(10.dp))
                                var assetsExpanded by remember { mutableStateOf(false) }
                                
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { assetsExpanded = !assetsExpanded }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (assetsExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight,
                                        contentDescription = if (assetsExpanded) "Collapse assets" else "Expand assets",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.Inventory,
                                        contentDescription = null,
                                        tint = Color.Gray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Assets ($assetsCount)",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Default
                                    )
                                }
                                
                                if (assetsExpanded) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    
                                    // 1. List all compiled/packaged binary release assets
                                    release.assets.forEach { asset ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 3.dp)
                                                .border(1.dp, Color(0xFF151515), RoundedCornerShape(4.dp))
                                                .background(Color(0xFF0A0A0A))
                                                .clickable {
                                                    downloadFileUsingManager(context, asset.browserDownloadUrl, asset.name)
                                                }
                                                .padding(10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                modifier = Modifier.weight(1f),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Download,
                                                    contentDescription = "Download binary asset",
                                                    tint = Color(0xFF00E5FF),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = asset.name,
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    fontFamily = FontFamily.Default,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            val kbSize = asset.size / 1024
                                            Text(
                                                text = if (kbSize > 1024) "${kbSize / 1024} MB" else "${kbSize} KB",
                                                color = Color.Gray,
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Default
                                            )
                                        }
                                    }

                                    // 2. Append Zipball source download (via codeload.github.com — bypasses api.github.com rate limit)
                                    val codeloadZipUrl = "https://codeload.github.com/${repoDetail?.owner?.login ?: ""}/${repoDetail?.name ?: ""}/zip/refs/tags/${release.tagName}"
                                    if (codeloadZipUrl.isNotBlank() && repoDetail != null) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 3.dp)
                                                .border(1.dp, Color(0xFF151515), RoundedCornerShape(4.dp))
                                                .background(Color(0xFF0A0A0A))
                                                .clickable {
                                                    val saveName = "${release.tagName}-source.zip"
                                                    downloadFileUsingManager(context, codeloadZipUrl, saveName)
                                                }
                                                .padding(10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                modifier = Modifier.weight(1f),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Source,
                                                    contentDescription = "Source code ZIP",
                                                    tint = Color(0xFF50FA7B),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "Source code (zip)",
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    fontFamily = FontFamily.Default
                                                )
                                            }
                                            Text(
                                                text = "ZIP Source",
                                                color = Color.Gray,
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Default
                                            )
                                        }
                                    }
                                    
                                    // 3. Append Tarball source download (via codeload.github.com — bypasses api.github.com rate limit)
                                    val codeloadTarUrl = "https://codeload.github.com/${repoDetail?.owner?.login ?: ""}/${repoDetail?.name ?: ""}/tar.gz/refs/tags/${release.tagName}"
                                    if (codeloadTarUrl.isNotBlank() && repoDetail != null) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 3.dp)
                                                .border(1.dp, Color(0xFF151515), RoundedCornerShape(4.dp))
                                                .background(Color(0xFF0A0A0A))
                                                .clickable {
                                                    val saveName = "${release.tagName}-source.tar.gz"
                                                    downloadFileUsingManager(context, codeloadTarUrl, saveName)
                                                }
                                                .padding(10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                modifier = Modifier.weight(1f),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Source,
                                                    contentDescription = "Source code TAR.GZ",
                                                    tint = Color(0xFF50FA7B),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "Source code (tar.gz)",
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    fontFamily = FontFamily.Default
                                                )
                                            }
                                            Text(
                                                text = "TAR.GZ Source",
                                                color = Color.Gray,
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Default
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Release notes popup — scraped from the web page, not the API
    viewingReleaseNotes?.let { release ->
        Dialog(onDismissRequest = {
            viewingReleaseNotes = null
            viewModel.clearReleaseNotes()
        }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.75f)
                    .border(2.dp, Color(0xFF00E5FF), RoundedCornerShape(8.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.Black)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Notes, contentDescription = null, tint = Color(0xFF00E5FF))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "RELEASE NOTES",
                                color = Color(0xFF00E5FF),
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Default
                            )
                        }
                        IconButton(onClick = {
                            viewingReleaseNotes = null
                            viewModel.clearReleaseNotes()
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Release name/tag header
                    Text(
                        text = release.name ?: release.tagName,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Default
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = release.tagName,
                        color = Color(0xFF8B949E),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Default
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Notes content area
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(Color(0xFF080808))
                            .border(1.dp, Color(0xFF151515))
                            .padding(12.dp)
                    ) {
                        when {
                            releaseNotesLoading -> {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        CircularProgressIndicator(
                                            color = Color(0xFF00E5FF),
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            "Scraping release notes from GitHub...",
                                            color = Color.Gray,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Default
                                        )
                                    }
                                }
                            }
                            releaseNotesContent != null -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    Text(
                                        text = releaseNotesContent!!,
                                        color = Color.LightGray,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Default
                                    )
                                }
                            }
                            else -> {
                                // Fallback: show the body from API (already loaded)
                                Text(
                                    text = release.body ?: "No release notes available.",
                                    color = Color.Gray,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Default
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UserProfileScreen(viewModel: GitHubViewModel, username: String) {
    val loading by viewModel.userProfileLoading.collectAsState()
    val user by viewModel.userProfileData.collectAsState()
    val repos by viewModel.userProfileRepos.collectAsState()
    val error by viewModel.userProfileError.collectAsState()
    val imageQuality by viewModel.imageQuality.collectAsState()
    val bookmarkedRepos by viewModel.bookmarkedRepos.collectAsState()

    // Trigger profile refresh when viewing if not present or changed
    LaunchedEffect(username) {
        if (user == null || user?.login != username) {
            viewModel.loadUserProfile(username)
        }
    }

    if (loading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color(0xFF00E5FF))
        }
        return
    }

    if (error != null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.AccountCircle, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text("User profiles lookup failed", color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Text(error!!, color = Color.Gray, fontSize = 12.sp, fontFamily = FontFamily.Default)
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { viewModel.loadUserProfile(username) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color.Black)
            ) {
                Text("RETRY", fontFamily = FontFamily.Default)
            }
        }
        return
    }

    val profile = user ?: return

    val scrollKey = "profile_${username}"
    val savedState = viewModel.getScrollState(scrollKey)
    val listState = rememberLazyListState()
    
    // Restore scroll position after repos load (handle empty list at composition time)
    LaunchedEffect(listState, repos) {
        if (repos.isNotEmpty()) {
            val targetIndex = savedState.first.coerceAtLeast(0)
            val targetOffset = savedState.second.coerceAtLeast(0)
            if (targetIndex > 0 || targetOffset > 0) {
                listState.scrollToItem(targetIndex, targetOffset)
            }
        }
    }
    
    DisposableEffect(listState) {
        onDispose {
            viewModel.saveScrollState(scrollKey, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
    ) {
        // User primary summary details
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF222222), RoundedCornerShape(8.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF060606))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OptimizedAvatar(
                        url = profile.avatarUrl,
                        contentDescription = "Avatar",
                        modifier = Modifier
                            .size(70.dp)
                            .background(Color.DarkGray),
                        quality = imageQuality
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = profile.name ?: profile.login,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "@${profile.login}",
                            color = Color(0xFF00E5FF),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Default
                        )
                        if (!profile.location.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(profile.location, color = Color.Gray, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        // Bio section
        if (!profile.bio.isNullOrEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFF1E1E1E), RoundedCornerShape(6.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF020202))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("BIO", color = Color(0xFF00E5FF), fontWeight = FontWeight.Black, fontSize = 11.sp, fontFamily = FontFamily.Default)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(profile.bio, color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
                    }
                }
            }
        }

        // Stats boxes grid mapped instantly
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatBox(label = "REPOS", value = "${profile.publicRepos ?: 0}", modifier = Modifier.weight(1f))
                StatBox(label = "FOLLOWERS", value = "${profile.followers ?: 0}", modifier = Modifier.weight(1f))
                StatBox(label = "FOLLOWING", value = "${profile.following ?: 0}", modifier = Modifier.weight(1f))
            }
        }

        // Repos list of this user
        item {
            Text(
                text = "USER REPOSITORIES (${repos.size})",
                color = Color(0xFF00E5FF),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 12.sp,
                fontFamily = FontFamily.Default
            )
        }

        if (repos.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("No public repositories discovered", color = Color.Gray, fontSize = 12.sp, fontFamily = FontFamily.Default)
                }
            }
        } else {
            items(repos, key = { it.id }) { repo ->
                val isPinned by remember(repo.id) {
                    derivedStateOf { bookmarkedRepos.any { it.id == repo.id } }
                }
                RepositoryRow(
                    repo = repo, 
                    onClick = {
                        viewModel.selectRepository(repo.owner.login, repo.name)
                    },
                    isBookmarked = isPinned,
                    onBookmarkClick = {
                        if (isPinned) viewModel.removeBookmark(repo.id) else viewModel.addBookmark(repo)
                    }
                )
            }
        }
    }
}

@Composable
fun SettingsScreen(viewModel: GitHubViewModel) {
    val currentToken by viewModel.accessToken.collectAsState()
    val savedUser by viewModel.activeHostUser.collectAsState()

    var inputToken by remember { mutableStateOf(currentToken) }
    var inputUser by remember { mutableStateOf(savedUser ?: "") }
    var keyVisible by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
    ) {
        // Unauthenticated rate-limit warning (only when no token is set)
        if (currentToken.isNullOrBlank()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFF332200), RoundedCornerShape(8.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0A00))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFFA726))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "UNAUTHENTICATED",
                                color = Color(0xFFFFA726),
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Default,
                                fontSize = 12.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Limited to 60 requests per hour. Generate a GitHub Personal Access Token below to lift this restriction and access private repositories.",
                            color = Color(0xFFBB8F4A),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Default
                        )
                    }
                }
            }
        }

        // VIP API key credentials config Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF222222), RoundedCornerShape(8.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.Black)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LockOpen, contentDescription = null, tint = Color(0xFF00E5FF))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "GITHUB PERSONAL TOKEN",
                            color = Color(0xFF00E5FF),
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Default,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = inputToken,
                        onValueChange = { inputToken = it.trim() },
                        label = { Text("Personal Access Token (PAT)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00E5FF),
                            unfocusedBorderColor = Color(0xFF333333),
                            focusedLabelColor = Color(0xFF00E5FF),
                            unfocusedLabelColor = Color.Gray,
                            focusedContainerColor = Color.Black,
                            unfocusedContainerColor = Color.Black,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { keyVisible = !keyVisible }) {
                                Icon(
                                    imageVector = if (keyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "Toggle Visibility",
                                    tint = Color.Gray
                                )
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Leave empty to browse anonymously (limited to 60 queries/hr). Generate a personal token at github.com/settings/tokens to lift restrictions.",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    if (inputToken != (currentToken ?: "")) {
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                keyboardController?.hide()
                                viewModel.saveToken(inputToken)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color.Black),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text("SAVE AND UPDATE PAT", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Default, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Active Host user configuration
        item {
            val currentSavedUser by viewModel.activeHostUser.collectAsState()
            val hasUserChanged = inputUser.trim() != (currentSavedUser ?: "")

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF222222), RoundedCornerShape(8.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.Black)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountCircle, contentDescription = null, tint = Color(0xFF00E5FF))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "DEFAULT TARGET USER",
                            color = Color(0xFF00E5FF),
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Default,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = inputUser,
                        onValueChange = { inputUser = it },
                        label = { Text("Default github handle") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00E5FF),
                            unfocusedBorderColor = Color(0xFF333333),
                            focusedLabelColor = Color(0xFF00E5FF),
                            unfocusedLabelColor = Color.Gray,
                            focusedContainerColor = Color.Black,
                            unfocusedContainerColor = Color.Black,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true
                    )
                    if (hasUserChanged) {
                    Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                keyboardController?.hide()
                                if (inputUser.isNotBlank()) {
                                    viewModel.saveHostUser(inputUser.trim())
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color.Black),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text("SAVE DEFAULT USER", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Default, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Proxy Configuration Card
        item {
            val proxyListState by viewModel.proxyList.collectAsState()
            val uaEnabled by viewModel.uaRotationEnabled.collectAsState()
            var proxyInput by remember { mutableStateOf(proxyListState.joinToString("\n")) }
            var uaToggle by remember(uaEnabled) { mutableStateOf(uaEnabled) }
            val activeCount = RetrofitClient.proxySelector.proxyCount()

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF222222), RoundedCornerShape(8.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.Black)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF00E5FF))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "RATE LIMIT EVASION",
                            color = Color(0xFF00E5FF),
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Default,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Rotate proxies and user-agents to bypass IP-based rate limiting and WAF fingerprinting.",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Default
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // UA rotation toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "USER-AGENT ROTATION",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Default
                            )
                            Text(
                                text = "Cycle browser UAs on each request",
                                color = Color.Gray,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Default
                            )
                        }
                        androidx.compose.material3.Switch(
                            checked = uaToggle,
                            onCheckedChange = {
                                uaToggle = it
                                viewModel.toggleUARotation(it)
                            },
                            colors = androidx.compose.material3.SwitchDefaults.colors(
                                checkedTrackColor = Color(0xFF00E5FF),
                                uncheckedTrackColor = Color(0xFF333333),
                                checkedThumbColor = Color.White,
                                uncheckedThumbColor = Color.Gray
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Proxy list
                    Text(
                        text = "PROXY POOL (one per line, format: host:port)",
                        color = Color(0xFF8B949E),
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Default
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = proxyInput,
                        onValueChange = { proxyInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 80.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 11.sp,
                            color = Color.White,
                            fontFamily = FontFamily.Default
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00E5FF),
                            unfocusedBorderColor = Color(0xFF333333),
                            focusedContainerColor = Color(0xFF0A0A0A),
                            unfocusedContainerColor = Color(0xFF0A0A0A),
                            cursorColor = Color(0xFF00E5FF)
                        ),
                        placeholder = {
                            Text(
                                text = "192.168.1.1:8080\n203.0.113.5:3128\n10.0.0.1:8888",
                                color = Color(0xFF555555),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Default
                            )
                        },
                        maxLines = 8
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (activeCount > 0) "$activeCount proxies active" else "No proxies configured — using direct connection",
                        color = if (activeCount > 0) Color(0xFF50FA7B) else Color.Gray,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Default
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            keyboardController?.hide()
                            val entries = proxyInput
                                .split("\n")
                                .map { it.trim() }
                                .filter { it.isNotBlank() }
                            viewModel.saveProxyList(entries)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color.Black),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text("SAVE PROXY LIST", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Default, fontSize = 12.sp)
                    }
                }
            }
        }

        // Image Quality Config Card
        item {
            val qState by viewModel.imageQuality.collectAsState()
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF222222), RoundedCornerShape(8.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.Black)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Image, contentDescription = null, tint = Color(0xFF00E5FF))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "CLIENT IMAGE LOAD",
                            color = Color(0xFF00E5FF),
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Default,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Show avatar, profile pics, and file images inside the client application.",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("ON", "OFF").forEach { option ->
                            val selected = qState == option
                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.setImageQuality(option) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (selected) Color(0xFF00E5FF) else Color(0xFF111111),
                                    contentColor = if (selected) Color.Black else Color.White
                                ),
                                shape = RoundedCornerShape(4.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(
                                    text = option,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Default,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Markdown Image Quality Config Card
        item {
            val qState by viewModel.markdownImageQuality.collectAsState()
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF222222), RoundedCornerShape(8.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.Black)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Article, contentDescription = null, tint = Color(0xFF00E5FF))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "HTML / MARKDOWN IMAGE LOAD",
                            color = Color(0xFF00E5FF),
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Default,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Support previewing and loading of inline images embedded inside README files.",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("ON", "OFF").forEach { option ->
                            val selected = qState == option
                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.setMarkdownImageQuality(option) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (selected) Color(0xFF00E5FF) else Color(0xFF111111),
                                    contentColor = if (selected) Color.Black else Color.White
                                ),
                                shape = RoundedCornerShape(4.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(
                                    text = option,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Default,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatBox(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.border(1.dp, Color(0xFF1C1C1C), RoundedCornerShape(4.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF030303))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                fontFamily = FontFamily.Default
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                color = Color.Gray,
                fontSize = 9.sp,
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun UnitBadge(label: String, color: Color) {
    Box(
        modifier = Modifier
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.08f))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Default
        )
    }
}

fun downloadFileUsingManager(context: android.content.Context, url: String, fileName: String) {
    try {
        val request = android.app.DownloadManager.Request(android.net.Uri.parse(url))
            .setTitle(fileName)
            .setDescription("Downloading $fileName")
            .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, fileName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
        
        val downloader = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
        downloader.enqueue(request)
        android.widget.Toast.makeText(context, "Download started: $fileName", android.widget.Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "Download error: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
    }
}

@Composable
fun FileViewScreen(
    viewModel: GitHubViewModel,
    owner: String,
    repo: String,
    path: String,
    downloadUrl: String
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val fileName = path.substringAfterLast('/')
    
    val activeViewFileName by viewModel.activeViewFileName.collectAsState()
    val activeViewFileContent by viewModel.activeViewFileContent.collectAsState()
    val activeViewFileLoading by viewModel.activeViewFileLoading.collectAsState()
    val activeViewFileError by viewModel.activeViewFileError.collectAsState()
    
    val imageQuality by viewModel.imageQuality.collectAsState()
    val markdownImageQuality by viewModel.markdownImageQuality.collectAsState()
    
    var searchQuery by remember { mutableStateOf("") }
    var isPreviewMode by remember { mutableStateOf(fileName.endsWith(".md", ignoreCase = true)) }

    // Search match navigation state
    var currentMatchIndex by remember { mutableIntStateOf(0) }
    var totalMatchCount by remember { mutableIntStateOf(0) }
    val searchMatchLines = remember(searchQuery, activeViewFileContent) {
        if (searchQuery.isBlank() || activeViewFileContent.isEmpty()) emptyList()
        else {
            val query = searchQuery
            activeViewFileContent.lines()
                .mapIndexedNotNull { index, line ->
                    if (line.contains(query, ignoreCase = true)) index else null
                }
        }
    }
    // Keep match count and index in sync
    LaunchedEffect(searchMatchLines.size) {
        totalMatchCount = searchMatchLines.size
        if (currentMatchIndex >= searchMatchLines.size) currentMatchIndex = 0
    }

    // Shared vertical scroll state for the code viewer (used by search navigation)
    val fileViewVerticalScrollState = rememberScrollState()
    val density = androidx.compose.ui.platform.LocalDensity.current

    // Scroll to the current match line when navigating
    LaunchedEffect(currentMatchIndex, searchMatchLines) {
        if (searchMatchLines.isNotEmpty() && currentMatchIndex < searchMatchLines.size) {
            val targetLine = searchMatchLines[currentMatchIndex]
            val lineHeightPx = with(density) { 18.dp.toPx() }
            fileViewVerticalScrollState.animateScrollTo((targetLine * lineHeightPx).toInt())
        }
    }

    // Intercept back action inside file view to clear search query, escaping code search mode
    BackHandler(enabled = searchQuery.isNotEmpty()) {
        searchQuery = ""
        currentMatchIndex = 0
        totalMatchCount = 0
    }
    
    // Auto load file content on enter
    LaunchedEffect(downloadUrl) {
        viewModel.viewFileContent(fileName, downloadUrl)
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D1117)) // Clean GitHub Dark canvas
            .padding(12.dp)
    ) {
        // Search & Action bar above the card
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Custom compact search box with border (no text clipping at 38dp)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .border(
                        width = 1.dp,
                        color = if (searchQuery.isNotEmpty()) Color(0xFF00E5FF) else Color(0xFF30363D),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .background(Color(0xFF090D13))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        currentMatchIndex = 0
                    },
                    modifier = Modifier.fillMaxSize(),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 12.sp,
                        color = Color.White
                    ),
                    singleLine = true,
                    decorationBox = { innerTextField ->
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        "Find text in file...",
                                        color = Color(0xFF8B949E),
                                        fontSize = 12.sp
                                    )
                                }
                                innerTextField()
                            }
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        searchQuery = ""
                                        currentMatchIndex = 0
                                        totalMatchCount = 0
                                    },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = "Clear",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                )
            }
            
            // Match counter and navigation buttons (only when searching)
            if (searchQuery.isNotEmpty() && totalMatchCount > 0) {
                Text(
                    text = "${currentMatchIndex + 1}/$totalMatchCount",
                    color = Color(0xFF8B949E),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.Medium
                )
                IconButton(
                    onClick = {
                        if (totalMatchCount > 0) {
                            currentMatchIndex = if (currentMatchIndex > 0) currentMatchIndex - 1 else totalMatchCount - 1
                        }
                    },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous match", tint = Color.White, modifier = Modifier.size(20.dp))
                }
                IconButton(
                    onClick = {
                        if (totalMatchCount > 0) {
                            currentMatchIndex = if (currentMatchIndex < totalMatchCount - 1) currentMatchIndex + 1 else 0
                        }
                    },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next match", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
            
            // Preview toggle button (only for .md files)
            if (fileName.endsWith(".md", ignoreCase = true)) {
                Button(
                    onClick = { isPreviewMode = !isPreviewMode },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPreviewMode) Color(0xFF00E5FF) else Color(0xFF21262D),
                        contentColor = if (isPreviewMode) Color.Black else Color.White
                    ),
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, if (isPreviewMode) Color(0xFF00E5FF) else Color(0xFF30363D)),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier.height(38.dp)
                ) {
                    Icon(if (isPreviewMode) Icons.Default.Code else Icons.Default.Preview, contentDescription = "Toggle Preview", modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isPreviewMode) "Code" else "Preview", fontWeight = FontWeight.SemiBold, fontSize = 10.sp)
                }
            }

            // Download raw button
            Button(
                onClick = {
                    if (downloadUrl.isNotEmpty()) {
                        downloadFileUsingManager(context, downloadUrl, fileName)
                    } else {
                        android.widget.Toast.makeText(context, "Download link not available", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF21262D),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(4.dp),
                border = BorderStroke(1.dp, Color(0xFF30363D)),
                contentPadding = PaddingValues(horizontal = 8.dp),
                modifier = Modifier.height(38.dp)
            ) {
                Icon(Icons.Default.Download, contentDescription = "Download Raw File", tint = Color.White, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Raw", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 10.sp)
            }
        }

        // Modern GitHub-style Code Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .border(1.dp, Color(0xFF30363D), RoundedCornerShape(6.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF090D13)), // Deep black-gray inside code viewer
            shape = RoundedCornerShape(6.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header of the card (identical to Github File Header)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF161B22))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = "File",
                            tint = Color(0xFF8B949E),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = fileName,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    
                    val linesCount = activeViewFileContent.lines().size
                    val byteCount = activeViewFileContent.length
                    val sizeFormatted = when {
                        byteCount >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", byteCount.toFloat() / (1024 * 1024))
                        byteCount >= 1024 -> String.format(java.util.Locale.US, "%.1f KB", byteCount.toFloat() / 1024)
                        else -> "$byteCount Bytes"
                    }
                    Text(
                        text = "$linesCount lines • $sizeFormatted",
                        color = Color(0xFF8B949E),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                HorizontalDivider(color = Color(0xFF30363D), thickness = 1.dp)

                // The File Content box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 4.dp, horizontal = 2.dp)
                ) {
                    if (activeViewFileLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color(0xFF00E5FF))
                        }
                    } else if (activeViewFileError != null) {
                        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                            Text(text = activeViewFileError!!, color = Color(0xFFF85149), fontSize = 12.sp)
                        }
                    } else {
                        if (isPreviewMode && activeViewFileContent.isNotEmpty()) {
                            Box(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
                                    val baseUrl = downloadUrl.substringBeforeLast("/")?.plus("/")
                                    MarkdownPreview(
                                        readme = activeViewFileContent,
                                        baseUrl = baseUrl,
                                        imageQuality = markdownImageQuality,
                                        searchQuery = searchQuery
                                    )
                            }
                        } else {
                            val isImageFile = fileName.endsWith(".png", ignoreCase = true) ||
                                              fileName.endsWith(".jpg", ignoreCase = true) ||
                                              fileName.endsWith(".jpeg", ignoreCase = true) ||
                                              fileName.endsWith(".webp", ignoreCase = true) ||
                                              fileName.endsWith(".gif", ignoreCase = true) ||
                                              fileName.endsWith(".svg", ignoreCase = true)
                            if (isImageFile && imageQuality != "DISABLE" && imageQuality != "OFF") {
                                Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                                    val request = coil.request.ImageRequest.Builder(context)
                                        .data(downloadUrl)
                                        .crossfade(false)
                                    if (fileName.endsWith(".svg", ignoreCase = true)) {
                                        request.decoderFactory(coil.decode.SvgDecoder.Factory())
                                    }
                                    request.memoryCacheKey("${downloadUrl}_cached")
                                    request.diskCacheKey("${downloadUrl}_cached")
                                    AsyncImage(
                                        model = request.build(),
                                        contentDescription = fileName,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                    )
                                }
                            } else if (isImageFile && (imageQuality == "DISABLE" || imageQuality == "OFF")) {
                                Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                                    Text(text = "[Image Viewing Disabled in Settings]", color = Color(0xFF8B949E), fontFamily = FontFamily.Default, fontSize = 12.sp)
                                }
                            } else {
                                val verticalScrollState = fileViewVerticalScrollState
                                val horizontalScrollState = rememberScrollState()
                                
                                val lineNumbersText = remember(activeViewFileContent) {
                                    val lineCount = activeViewFileContent.lines().size
                                    (1..lineCount).joinToString("\n") { it.toString() }
                                }
                                val highlightedCodeState = produceState<AnnotatedString>(
                                    initialValue = AnnotatedString(activeViewFileContent),
                                    key1 = activeViewFileContent,
                                    key2 = fileName,
                                    key3 = searchQuery
                                ) {
                                    value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                                        highlightCode(activeViewFileContent, fileName, searchQuery)
                                    }
                                }
                                val highlightedCode = highlightedCodeState.value

                                SelectionContainer {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .verticalScroll(verticalScrollState)
                                    ) {
                                        // Beautiful Line Numbers column (Gutter)
                                        Text(
                                            text = lineNumbersText,
                                            color = Color(0xFF484F58),
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Default,
                                            fontWeight = FontWeight.Medium,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                            modifier = Modifier
                                                .padding(end = 12.dp, start = 8.dp, top = 8.dp)
                                                .widthIn(min = 28.dp)
                                        )

                                        // Code lines
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .horizontalScroll(horizontalScrollState)
                                                .padding(top = 8.dp, bottom = 8.dp, end = 12.dp)
                                        ) {
                                            Text(
                                                text = highlightedCode,
                                                color = Color(0xFFC9D1D9),
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Default,
                                                fontWeight = FontWeight.Normal
                                            )
                                        }
                                    }
                                }
                        }
                    }
                }
            }
         }
     }
 }
}
