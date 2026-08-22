import { useEffect, useMemo, useState } from 'react';
import { Document as PdfDoc, Page, pdfjs } from 'react-pdf';
import 'react-pdf/dist/esm/Page/AnnotationLayer.css';
import 'react-pdf/dist/esm/Page/TextLayer.css';
import { getDocumentFileUrl } from '../api/documents';
import { useAuth } from '../context/AuthContext';
import { useSession } from '../hooks/useSession';

pdfjs.GlobalWorkerOptions.workerSrc = new URL(
  'pdfjs-dist/build/pdf.worker.min.mjs',
  import.meta.url
).toString();

interface Props {
  sessionId: number;
}

export default function PdfViewer({ sessionId }: Props) {
  const { token } = useAuth();
  const { session, error, goToPage } = useSession(sessionId);
  const [numPages, setNumPages] = useState(0);
  const [inputPage, setInputPage] = useState(1);

  useEffect(() => {
    if (session) setInputPage(session.currentPage);
  }, [session?.currentPage]);

  const documentId = session?.documentId;
  const file = useMemo(
    () =>
      documentId
        ? {
            url: getDocumentFileUrl(documentId),
            httpHeaders: token ? { Authorization: `Bearer ${token}` } : undefined
          }
        : null,
    [documentId, token]
  );

  if (!session || !file) {
    return (
      <div className="flex h-full items-center justify-center text-booki-muted">
        <span className="mr-2 h-5 w-5 animate-spin rounded-full border-2 border-white/20 border-t-booki-accent" />
        Loading session…
      </div>
    );
  }

  const onGoToPage = async (page: number) => {
    await goToPage(page);
  };

  return (
    <div className="flex h-full flex-col">
      <div className="flex items-center justify-between border-b border-white/10 bg-booki-surface/50 px-6 py-3 backdrop-blur">
        <div className="min-w-0 flex-1">
          <h2 className="truncate text-sm font-bold text-white">{session.title}</h2>
          <p className="text-xs text-booki-muted">Session pages {session.startPage}-{session.endPage}</p>
          {error && <p className="mt-0.5 text-xs text-rose-400">{error}</p>}
        </div>
        <div className="flex items-center gap-3">
          <div className="flex items-center gap-2 rounded-lg bg-booki-card px-3 py-1.5">
            <button
              onClick={() => onGoToPage(session.currentPage - 1)}
              disabled={session.currentPage <= session.startPage}
              className="rounded p-1 text-white/70 transition hover:bg-white/10 hover:text-white disabled:opacity-30"
            >
              <svg className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" d="M15 19l-7-7 7-7" />
              </svg>
            </button>
            <span className="text-xs text-white/60">Page</span>
            <input
              type="number"
              min={session.startPage}
              max={session.endPage}
              value={inputPage}
              onChange={(e) => setInputPage(Number(e.target.value))}
              onBlur={() => onGoToPage(inputPage)}
              onKeyDown={(e) => e.key === 'Enter' && onGoToPage(inputPage)}
              className="w-12 bg-transparent text-center text-sm font-bold text-white outline-none"
            />
            <span className="text-xs text-white/60">/ {session.endPage}</span>
            <button
              onClick={() => onGoToPage(session.currentPage + 1)}
              disabled={session.currentPage >= session.endPage}
              className="rounded p-1 text-white/70 transition hover:bg-white/10 hover:text-white disabled:opacity-30"
            >
              <svg className="h-4 w-4" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
              </svg>
            </button>
          </div>
        </div>
      </div>

      <div className="flex-1 overflow-auto bg-booki-bg/50 p-4 md:p-8">
        <div className="mx-auto w-fit">
          <PdfDoc
            file={file}
            onLoadSuccess={({ numPages }) => setNumPages(numPages)}
            loading={
              <div className="flex h-96 w-72 items-center justify-center rounded-lg bg-booki-card text-booki-muted md:w-[600px]">
                Loading PDF…
              </div>
            }
            error={
              <div className="flex h-96 w-72 items-center justify-center rounded-lg bg-booki-card text-booki-muted md:w-[600px]">
                The PDF could not be loaded.
              </div>
            }
          >
            <Page
              pageNumber={session.currentPage}
              renderTextLayer
              renderAnnotationLayer
              className="rounded-lg shadow-2xl"
            />
          </PdfDoc>
        </div>
        <p className="mt-4 text-center text-xs text-booki-muted">
          PDF total: {numPages} pages · Current session page: {session.currentPage}
        </p>
      </div>
    </div>
  );
}
