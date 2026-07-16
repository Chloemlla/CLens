package com.chloemlla.clens.ui.editor

/**
 * Path segment for a document field.
 * Objects use [Key], arrays use [Index].
 */
sealed class PathSegment {
    data class Key(val name: String) : PathSegment()
    data class Index(val index: Int) : PathSegment()

    override fun toString(): String = when (this) {
        is Key -> name
        is Index -> "[$index]"
    }
}

enum class DocValueType {
    Object,
    Array,
    String,
    Int32,
    Int64,
    Double,
    Boolean,
    Null,
    ObjectId,
    Date,
    Binary,
    GeoPoint,
    Raw,
}

/**
 * Immutable document tree node used by the structured editor.
 */
data class DocNode(
    val path: List<PathSegment> = emptyList(),
    val key: String? = null,
    val type: DocValueType,
    val children: List<DocNode>? = null,
    val scalar: String? = null,
    val collapsed: Boolean = true,
    val error: String? = null,
) {
    val pathKey: String
        get() = path.joinToString(".") { segment ->
            when (segment) {
                is PathSegment.Key -> segment.name
                is PathSegment.Index -> segment.index.toString()
            }
        }

    val isContainer: Boolean
        get() = type == DocValueType.Object || type == DocValueType.Array

    val displayLabel: String
        get() = key ?: path.lastOrNull()?.toString() ?: "root"
}

enum class DocumentEditorMode {
    Tree,
    Code,
}

enum class DocumentEditorSource {
    InsertBlank,
    SelectedDocument,
    ImportedJson,
}

data class DocumentEditorState(
    val mode: DocumentEditorMode = DocumentEditorMode.Tree,
    val root: DocNode = DocNodeCodec.emptyObject(),
    val selectedPath: String = "",
    val codeText: String = "{\n  \n}",
    val codeDiagnostics: List<String> = emptyList(),
    val dirty: Boolean = false,
    val draftId: String? = null,
    val source: DocumentEditorSource = DocumentEditorSource.InsertBlank,
    val editingPath: String? = null,
    val draftBanner: String? = null,
    val parseError: String? = null,
) {
    val canApplyCode: Boolean
        get() = codeDiagnostics.isEmpty() && parseError == null
}

/**
 * Flattened row used by LazyColumn tree rendering.
 */
data class DocTreeRow(
    val node: DocNode,
    val depth: Int,
    val isExpandable: Boolean,
)
