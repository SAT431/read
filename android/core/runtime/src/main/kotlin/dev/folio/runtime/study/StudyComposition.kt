package dev.folio.runtime.study

import dev.folio.model.study.*

data class StudySurface(val root: StudyPageReference,val sections: List<StudyPageReference>)

/** Logical insertion order and reading surfaces retain exact original ownership. */
object StudyComposition {
    fun pages(sources: List<StudySource>): List<StudyPageReference> {
        val all=sources.flatMap{source->source.plan.pages.map{StudyPageReference(source,it)}}
        val output=mutableListOf<StudyPageReference>();val visited=mutableSetOf<Pair<String,Int>>()
        val children=sources.filter{it.placement!=null}.groupBy{it.placement!!}
        fun visit(ref:StudyPageReference) {
            require(visited.add(ref.source.pdf.id to ref.pageIndex)){"Circular study placement"}
            output.add(ref)
            children[StudyPlacement(ref.source.pdf.id,ref.pageIndex)].orEmpty().forEach{child->
                child.plan.pages.forEach{visit(StudyPageReference(child,it))}
            }
        }
        all.filter{it.source.placement==null}.forEach(::visit)
        require(output.size==all.size){"Study insertion point unavailable"}
        return output
    }
    fun surface(plan: DocumentStudyPlan,sources: List<StudySource>): StudySurface {
        val all=pages(sources)
        fun root(ref:StudyPageReference):StudyPageReference {
            var current=ref;val visited=mutableSetOf<String>()
            while(true) {
                require(visited.add(current.source.pdf.id)){"Circular study placement"}
                val at=current.source.placement ?: return current
                current=all.firstOrNull{it.source.pdf.id==at.afterPdfId && it.pageIndex==at.afterPageIndex}
                    ?: error("Study insertion point unavailable")
            }
        }
        val active=all.first{it.source.pdf.id==(plan.activeSourcePdfId ?: plan.pdfId) && it.pageIndex==plan.pageIndex}
        val host=root(active)
        return StudySurface(host,all.filter{val owner=root(it);owner.source.pdf.id==host.source.pdf.id && owner.pageIndex==host.pageIndex})
    }
    fun normalizePrimary(pdfId:String,pages:List<Int>,links:List<StudySourceSelection>) = links.map{link->
        val placement=link.placement
        if(placement?.afterPdfId==pdfId && placement.afterPageIndex !in pages)
            link.copy(placement=pages.firstOrNull()?.let{StudyPlacement(pdfId,it)}) else link
    }
}
