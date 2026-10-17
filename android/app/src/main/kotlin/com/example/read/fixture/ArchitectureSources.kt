package com.example.read.fixture

import dev.folio.local.catalog.InMemorySourceCatalog
import dev.folio.model.source.*

/** Original synthetic text for architecture testing. No fixture is presented as NCERT. */
object ArchitectureSources {
    const val DOCUMENT_ID = "fixture-cell"
    const val CHAPTER_ID = "fixture-chapter"
    fun catalog() = InMemorySourceCatalog(
        listOf(SourceDocument(DOCUMENT_ID, "Cell architecture fixture", 2, "fixture-only", null)),
        listOf(Chapter(CHAPTER_ID, "Cell architecture fixture", DOCUMENT_ID, 0, 1)),
        listOf(
            SourcePage(DOCUMENT_ID, 0, "A small world. A remarkable system.", listOf(
                SourceElement("cell-heading", SourceElementKind.HEADING, "Inside the eukaryotic cell", 0),
                SourceElement("cell-p1", SourceElementKind.PARAGRAPH,
                    "Eukaryotic cells contain a membrane-bound nucleus and specialised organelles. Their compartments support different cellular processes.", 1),
                SourceElement("cell-p2", SourceElementKind.PARAGRAPH,
                    "Rough endoplasmic reticulum carries surface ribosomes and participates in protein synthesis. Smooth endoplasmic reticulum lacks ribosomes and participates in lipid synthesis.", 2),
                SourceElement("cell-figure", SourceElementKind.FIGURE, "Original figure adapter contract", 3),
            ), isFixture = true),
            SourcePage(DOCUMENT_ID, 1, "Connections inside a cell.", listOf(
                SourceElement("cell-p3", SourceElementKind.PARAGRAPH,
                    "The Golgi apparatus modifies, sorts and packages cellular products. Transport vesicles connect cellular compartments.", 0),
            ), isFixture = true),
        ),
    )
}
