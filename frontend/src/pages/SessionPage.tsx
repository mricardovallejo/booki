import { useParams } from 'react-router-dom';
import PdfViewer from '../components/PdfViewer';
import SessionSidebar from '../components/SessionSidebar';

export default function SessionPage() {
  const { sessionId } = useParams<{ sessionId: string }>();
  const id = Number(sessionId);

  if (!id) return <p className="p-6 text-white">Invalid session</p>;

  return (
    <div className="flex h-[calc(100vh-64px)] flex-col md:flex-row">
      <section className="flex-1 overflow-hidden">
        <PdfViewer sessionId={id} />
      </section>
      <SessionSidebar sessionId={id} />
    </div>
  );
}
