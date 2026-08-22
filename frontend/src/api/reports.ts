import api from './client';
import { ENDPOINTS } from '../config/endpoints';

export const downloadReportFile = (reportId: number) =>
  api.get(ENDPOINTS.reports.file(reportId), { responseType: 'blob' }).then((r) => r.data as Blob);
