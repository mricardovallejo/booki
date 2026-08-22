import { useRef, useState } from 'react';
import { useDocuments } from '../hooks/useDocuments';
import { useTags } from '../hooks/useTags';
import { useScrollToHash } from '../hooks/useScrollToHash';
import CreateSessionModal from '../components/CreateSessionModal';
import TagPickerModal from '../components/TagPickerModal';
import TagsBar from '../components/TagsBar';
import HeroSection from '../components/HeroSection';
import HorizontalRow from '../components/HorizontalRow';
import ConfirmDialog from '../components/ConfirmDialog';
import Button from '../components/ui/Button';
import { getErrorMessage } from '../lib/errors';
import type { Document } from '../types';

export default function HomePage() {
  useScrollToHash();
  const { documents, isUploading, upload, remove: removeDocument } = useDocuments();
  const { tags, error: tagsError, create, rename, remove: removeTag, toggleDocument } = useTags();
  const [selectedDocument, setSelectedDocument] = useState<Document | null>(null);
  const [organizingDocument, setOrganizingDocument] = useState<Document | null>(null);
  const [deletingDocument, setDeletingDocument] = useState<Document | null>(null);
  const [query, setQuery] = useState('');
  const [error, setError] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const onUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setError(null);
    try {
      await upload(file);
    } catch (err) {
      setError(getErrorMessage(err, 'Could not upload this file.'));
    } finally {
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  };

  const onConfirmDelete = async () => {
    if (!deletingDocument) return;
    setError(null);
    try {
      await removeDocument(deletingDocument.id);
      setDeletingDocument(null);
    } catch (err) {
      setError(getErrorMessage(err, 'Could not remove this book.'));
    }
  };

  const recent = [...documents].sort(
    (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
  );
  const largeDocs = [...documents].sort((a, b) => b.pageCount - a.pageCount).slice(0, 6);

  const trimmedQuery = query.trim().toLowerCase();
  const searchResults = trimmedQuery
    ? documents.filter((d) => d.title.toLowerCase().includes(trimmedQuery))
    : [];

  return (
    <div className="min-h-screen bg-booki-bg pb-20">
      <HeroSection onUploadClick={() => fileInputRef.current?.click()} hasDocuments={documents.length > 0} />

      <div className="mx-auto max-w-7xl scroll-mt-20 px-6 pt-8" id="library">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h2 className="text-2xl font-bold text-white">Your library</h2>
            <p className="text-sm text-booki-muted">{documents.length} books or documents</p>
          </div>
          <div className="flex items-center gap-3">
            <div className="relative">
              <svg
                className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-white/40"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                viewBox="0 0 24 24"
              >
                <path strokeLinecap="round" strokeLinejoin="round" d="M21 21l-4.35-4.35M17 10.5A6.5 6.5 0 114 10.5a6.5 6.5 0 0113 0z" />
              </svg>
              <input
                type="search"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="Search your library…"
                className="w-56 rounded-lg bg-booki-card py-2 pl-9 pr-3 text-sm text-white outline-none ring-1 ring-white/10 placeholder-white/40 focus:ring-booki-accent"
              />
            </div>
            <input
              ref={fileInputRef}
              type="file"
              accept="application/pdf"
              className="hidden"
              onChange={onUpload}
            />
            <Button onClick={() => fileInputRef.current?.click()} disabled={isUploading}>
              {isUploading ? (
                <>
                  <span className="h-4 w-4 animate-spin rounded-full border-2 border-white/30 border-t-white" />
                  Uploading…
                </>
              ) : (
                <>
                  <svg className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" d="M12 4v16m8-8H4" />
                  </svg>
                  Upload PDF
                </>
              )}
            </Button>
          </div>
        </div>
        {error && <p className="mt-3 text-sm text-rose-400">{error}</p>}
      </div>

      {tagsError && <p className="mx-auto max-w-7xl px-6 text-sm text-rose-400">{tagsError}</p>}

      {documents.length > 0 && !trimmedQuery && (
        <TagsBar tags={tags} onCreate={create} onRename={rename} onDelete={removeTag} />
      )}

      {trimmedQuery ? (
        <HorizontalRow
          title={`Search results for "${query.trim()}"`}
          documents={searchResults}
          onSelect={setSelectedDocument}
          onOrganize={setOrganizingDocument}
          onDelete={setDeletingDocument}
        />
      ) : (
        <>
          <HorizontalRow
            title="Recently added"
            documents={recent}
            onSelect={setSelectedDocument}
            onOrganize={setOrganizingDocument}
            onDelete={setDeletingDocument}
          />

          {tags.map((tag) => {
            const docsInTag = documents.filter((d) => tag.documentIds.includes(d.id));
            return (
              <HorizontalRow
                key={tag.id}
                title={tag.name}
                documents={docsInTag}
                onSelect={setSelectedDocument}
                onOrganize={setOrganizingDocument}
                onDelete={setDeletingDocument}
              />
            );
          })}

          <HorizontalRow
            title="Long reads"
            documents={largeDocs}
            onSelect={setSelectedDocument}
            onOrganize={setOrganizingDocument}
            onDelete={setDeletingDocument}
          />
        </>
      )}

      {trimmedQuery && searchResults.length === 0 && (
        <p className="px-6 text-sm text-booki-muted">No books match "{query.trim()}".</p>
      )}

      {documents.length === 0 && (
        <div className="mx-auto max-w-xl px-6 py-20 text-center">
          <div className="rounded-2xl bg-booki-surface p-10">
            <div className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-booki-card">
              <svg className="h-8 w-8 text-booki-accent" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
              </svg>
            </div>
            <h3 className="text-xl font-bold text-white">Your library is empty</h3>
            <p className="mt-2 text-sm text-booki-muted">
              Upload your first PDF to start creating reading sessions with BooKI.
            </p>
          </div>
        </div>
      )}

      <CreateSessionModal document={selectedDocument} onClose={() => setSelectedDocument(null)} />

      <TagPickerModal
        document={organizingDocument}
        tags={tags}
        onToggle={toggleDocument}
        onClose={() => setOrganizingDocument(null)}
      />

      <ConfirmDialog
        open={!!deletingDocument}
        title="Remove this book?"
        description={
          deletingDocument
            ? `"${deletingDocument.title}" and all of its sessions, chats, and quiz history will be deleted. This can't be undone.`
            : undefined
        }
        confirmLabel="Remove"
        onConfirm={onConfirmDelete}
        onCancel={() => setDeletingDocument(null)}
      />
    </div>
  );
}
