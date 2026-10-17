package dev.folio.runtime

import dev.folio.model.port.EngineeringRequest
import dev.folio.model.source.*
import dev.folio.runtime.engineering.*
import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking
import dev.folio.model.port.*

class FigureCropPolicyTest {
    @Test fun approximateFigureBoxRetainsPageSideLabelsAndAddsBoundedVerticalContext() {
        val box=SourceBounds(140f,100f,450f,700f)
        val crop=FigureCropPolicy.context(box,600f,800f)
        assertEquals(SourceBounds(0f,68f,600f,732f),crop)
        assertEquals(SourceBounds(0f,0f,600f,800f),FigureCropPolicy.context(SourceBounds(10f,1f,590f,799f),600f,800f))
    }
    @Test fun cropCannotEscapePageOrDiscardOriginalRegionAndLegacyDataStillLoads() {
        val request=EngineeringRequest("fixture","doc",0,"hash",2)
        val figure=SourceElement("figure",SourceElementKind.FIGURE,"Controlled",0,SourceBounds(100f,100f,300f,300f),mediaId="fixture.png")
        val page=SourcePage("doc",0,"Controlled",listOf(figure),engineeringVersion=2,widthPoints=600f,heightPoints=800f)
        PageModelValidator().validate(request,page)
        PageModelValidator().validate(request,page.copy(elements=listOf(figure.copy(mediaBounds=FigureCropPolicy.context(figure.bounds!!,600f,800f)))))
        assertTrue(runCatching{PageModelValidator().validate(request,page.copy(elements=listOf(figure.copy(mediaBounds=SourceBounds(110f,100f,300f,300f)))))}.isFailure)
        assertTrue(runCatching{FigureCropPolicy.context(figure.bounds!!,200f,800f)}.isFailure)
    }
    @Test fun legacyFigureAssetUpgradeIsLocalAndPreservesSourceIdsWordingAndCorrections()=runBlocking {
        var crops=0
        val tools=object:PdfTools{
            override suspend fun render(documentId:String,pageIndex:Int)="unused.png"
            override suspend fun crop(documentId:String,pageIndex:Int,bounds:SourceBounds):String{crops++;return "new-crop.png"}
        }
        val element=SourceElement("stable",SourceElementKind.FIGURE,"Original labels",0,SourceBounds(100f,100f,300f,300f),mediaId="old.png")
        val saved=SourcePage("doc",0,"Original",listOf(element),engineeringVersion=2,widthPoints=600f,heightPoints=800f)
        var cached=saved;var calls=0
        val repository=object:PageRepository{
            override suspend fun find(key:PageCacheKey)=cached
            override suspend fun save(key:PageCacheKey,page:SourcePage){cached=page}
        }
        val coordinator=EngineeringCoordinator(object:PageEngineer{
            override suspend fun engineer(request:EngineeringRequest):SourcePage{calls++;error("No model call allowed")}
        },repository,cachedUpgrade=FigureAssetUpgrade(tools)::upgrade)
        val request=EngineeringRequest("test","doc",0,"hash",2)
        val upgraded=coordinator.ensure(request);coordinator.ensure(request)
        assertEquals(0,calls);assertEquals(1,crops)
        assertEquals(saved,upgraded.copy(elements=listOf(upgraded.elements.single().copy(mediaId=element.mediaId,mediaBounds=null))))
        assertNotNull(upgraded.elements.single().mediaBounds)
    }
}
