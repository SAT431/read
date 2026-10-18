import React, { useEffect, useRef, useState } from "react";
import {
  ArrowLeft,
  ChevronLeft,
  ChevronRight,
  Download,
  LayoutList,
  RotateCw,
  Search,
  Sparkles,
  ZoomIn,
  ZoomOut,
  X,
} from "lucide-react";
import { LibraryPdfItem } from "../data/neetCatalog";
import {
  BUILTIN_NCERT_CELL_PAGES,
  ExtractedPdfPage,
} from "../services/pdfEngine";

interface GoogleDrivePdfViewerProps {
  pdf: LibraryPdfItem;
  extractedPages: ExtractedPdfPage[];
  onBack: () => void;
  onOpenStudyEngine: () => void;
}

export const GoogleDrivePdfViewer: React.FC<GoogleDrivePdfViewerProps> = ({
  pdf,
  extractedPages,
  onBack,
  onOpenStudyEngine,
}) => {
  const [currentPage, setCurrentPage] = useState(1);
  const [zoom, setZoom] = useState(100);
  const [rotation, setRotation] = useState(0);
  const [showThumbnails, setShowThumbnails] = useState(false);
  const [showSearch, setShowSearch] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");

  const viewportRef = useRef<HTMLDivElement>(null);
  const pageRefs = useRef<Record<number, HTMLDivElement | null>>({});

  const totalPages = pdf.isBuiltInNcert
    ? BUILTIN_NCERT_CELL_PAGES.length
    : Math.max(1, extractedPages.length || pdf.pageCount);

  const jumpToPage = (pageNo: number) => {
    const clamped = Math.max(1, Math.min(totalPages, pageNo));
    setCurrentPage(clamped);
    const el = pageRefs.current[clamped];
    if (el) {
      el.scrollIntoView({ behavior: "smooth", block: "start" });
    }
  };

  useEffect(() => {
    const container = viewportRef.current;
    if (!container) return;

    const handleScroll = () => {
      const containerRect = container.getBoundingClientRect();
      const midY = containerRect.top + containerRect.height * 0.35;
      for (let i = 1; i <= totalPages; i++) {
        const el = pageRefs.current[i];
        if (el) {
          const rect = el.getBoundingClientRect();
          if (rect.top <= midY && rect.bottom >= midY) {
            setCurrentPage(i);
            break;
          }
        }
      }
    };

    container.addEventListener("scroll", handleScroll, { passive: true });
    return () => container.removeEventListener("scroll", handleScroll);
  }, [totalPages]);

  const highlightSearch = (text: string) => {
    if (!searchQuery.trim()) return text;
    const escaped = searchQuery.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
    const parts = text.split(new RegExp(`(${escaped})`, "gi"));
    return parts.map((part, idx) =>
      part.toLowerCase() === searchQuery.toLowerCase() ? (
        <mark key={idx} className="gdrive-pdf-mark">
          {part}
        </mark>
      ) : (
        part
      )
    );
  };

  const renderBuiltInNcertPage = (pageNum: number) => {
    const pageData =
      BUILTIN_NCERT_CELL_PAGES.find((p) => p.pageNumber === pageNum) ||
      BUILTIN_NCERT_CELL_PAGES[0];

    return (
      <div
        className="ncert-sheet-page"
        style={{
          width: `${Math.round(780 * (zoom / 100))}px`,
          transform: `rotate(${rotation}deg)`,
          fontSize: `${Math.max(12, Math.round(14.5 * (zoom / 100)))}px`,
        }}
      >
        <div className="ncert-sheet-running-header">
          <span>{pageData.headerLeft}</span>
          <span>{pageData.headerRight}</span>
        </div>

        {pageData.chapterBanner && (
          <div className="ncert-sheet-banner">
            <div className="ncert-sheet-unit">
              {pageData.chapterBanner.unitInfo}
            </div>
            <div className="ncert-sheet-chapno">
              {pageData.chapterBanner.chapterNo}
            </div>
            <h1 className="ncert-sheet-title">
              {pageData.chapterBanner.title}
            </h1>
          </div>
        )}

        <div className="ncert-sheet-columns">
          {pageData.columns.map((col, idx) => (
            <div key={idx} className="ncert-sheet-col">
              {col.heading && (
                <h3 className="ncert-sheet-heading">
                  {highlightSearch(col.heading)}
                </h3>
              )}
              {col.paragraphs.map((p, pIdx) => (
                <p key={pIdx} className="ncert-sheet-para">
                  {highlightSearch(p)}
                </p>
              ))}
            </div>
          ))}
        </div>

        {pageData.figure && (
          <div className="ncert-sheet-figure">
            <img
              src={pageData.figure.svgDataUrl}
              alt={pageData.figure.caption}
            />
            <div className="ncert-sheet-figcaption">
              <strong>{pageData.figure.label}:</strong>{" "}
              {highlightSearch(pageData.figure.caption)}
            </div>
          </div>
        )}

        <div className="ncert-sheet-footer">
          <span>2025-26 · Rationalised NCERT Textbook</span>
          <span>Page {pageData.pageNumber}</span>
        </div>
      </div>
    );
  };

  const renderUploadedPdfPage = (pageNum: number) => {
    const p = extractedPages.find((x) => x.pageNumber === pageNum);
    return (
      <div
        className="ncert-sheet-page uploaded-sheet"
        style={{
          width: `${Math.round(780 * (zoom / 100))}px`,
          transform: `rotate(${rotation}deg)`,
        }}
      >
        {p?.pageImageDataUrl ? (
          <img
            src={p.pageImageDataUrl}
            alt={`PDF Page ${pageNum}`}
            style={{ width: "100%", height: "auto", display: "block" }}
          />
        ) : (
          <div style={{ padding: 36 }}>
            <div className="ncert-sheet-running-header">
              <span>NCERT SOURCE</span>
              <span>PAGE {pageNum}</span>
            </div>
            <p className="ncert-sheet-para">
              {highlightSearch(p?.text || `Page ${pageNum}`)}
            </p>
          </div>
        )}
      </div>
    );
  };

  const pagesToShow = Array.from({ length: totalPages }, (_, i) => i + 1);

  return (
    <div className="gdrive-pdf-shell" data-testid="native-pdf-viewer">
      {/* Main Body: 100% Full-Screen PDF Canvas (No Top Filename / Page Navigation Bar) */}
      <div className="gdrive-pdf-body">
        {showThumbnails && (
          <aside className="gdrive-thumbnails-drawer">
            <div className="gdrive-thumbnails-header">Pages</div>
            <div className="gdrive-thumbnails-list">
              {Array.from({ length: totalPages }, (_, i) => i + 1).map(
                (pageNo) => (
                  <button
                    key={pageNo}
                    className={`gdrive-thumb-item ${
                      currentPage === pageNo ? "active" : ""
                    }`}
                    onClick={() => jumpToPage(pageNo)}
                  >
                    <div className="gdrive-thumb-preview">
                      <span>p. {pageNo}</span>
                    </div>
                    <span className="gdrive-thumb-label">Page {pageNo}</span>
                  </button>
                )
              )}
            </div>
          </aside>
        )}

        <div className="gdrive-pdf-viewport" ref={viewportRef}>
          {pagesToShow.map((pageNo) => (
            <div
              key={pageNo}
              ref={(el) => {
                pageRefs.current[pageNo] = el;
              }}
              className="gdrive-page-wrapper"
            >
              {pdf.isBuiltInNcert
                ? renderBuiltInNcertPage(pageNo)
                : renderUploadedPdfPage(pageNo)}
            </div>
          ))}
        </div>
      </div>

      {/* Compact Floating Search Box (Only when Search toggled) */}
      {showSearch && (
        <div
          style={{
            position: "fixed",
            bottom: 70,
            left: "50%",
            transform: "translateX(-50%)",
            zIndex: 60,
          }}
          className="gdrive-search-box"
        >
          <Search size={14} />
          <input
            type="text"
            placeholder="Find in PDF…"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            autoFocus
          />
          <button
            className="gdrive-icon-btn small"
            onClick={() => {
              setShowSearch(false);
              setSearchQuery("");
            }}
          >
            <X size={14} />
          </button>
        </div>
      )}

      {/* Unified Minimal Bottom Floating Pill: Back, Prev/Next Page, Zoom, & Study Engine */}
      <div className="gdrive-floating-zoom-pill">
        <button
          onClick={onBack}
          title="Back to Library"
          data-testid="pdf-back-button"
        >
          <ArrowLeft size={15} />
        </button>
        <button
          onClick={() => setShowThumbnails((s) => !s)}
          title="Toggle Page Thumbnails"
        >
          <LayoutList size={15} />
        </button>
        <span className="gdrive-pill-divider" />
        <button
          disabled={currentPage <= 1}
          onClick={() => jumpToPage(currentPage - 1)}
          title="Previous Page"
        >
          <ChevronLeft size={15} />
        </button>
        <span className="gdrive-pill-page">
          {currentPage} / {totalPages}
        </span>
        <button
          disabled={currentPage >= totalPages}
          onClick={() => jumpToPage(currentPage + 1)}
          title="Next Page"
        >
          <ChevronRight size={15} />
        </button>
        <span className="gdrive-pill-divider" />
        <button
          onClick={() => setZoom((z) => Math.max(60, z - 15))}
          title="Zoom Out"
        >
          <ZoomOut size={15} />
        </button>
        <span className="gdrive-pill-zoom">{zoom}%</span>
        <button
          onClick={() => setZoom((z) => Math.min(220, z + 15))}
          title="Zoom In"
        >
          <ZoomIn size={15} />
        </button>
        <button
          onClick={() => setRotation((r) => (r + 90) % 360)}
          title="Rotate 90°"
        >
          <RotateCw size={14} />
        </button>
        <button
          onClick={() => setShowSearch((s) => !s)}
          title="Search in PDF"
        >
          <Search size={14} />
        </button>
        {pdf.dataUrl && (
          <a
            href={pdf.dataUrl}
            download={pdf.fileName}
            style={{ color: "#fff", display: "inline-flex" }}
            title="Download PDF"
          >
            <Download size={14} />
          </a>
        )}
        <span className="gdrive-pill-divider" />
        <button
          className="gdrive-pill-fit"
          onClick={onOpenStudyEngine}
          data-testid="open-study-engine-from-pdf"
          title="Open in AI Study Engine"
          style={{
            background: "linear-gradient(135deg, #6d3fe2, #532bc4)",
            display: "inline-flex",
            alignItems: "center",
            gap: 5,
          }}
        >
          <Sparkles size={13} />
          <span>Study Engine</span>
        </button>
      </div>
    </div>
  );
};
