'use client';
import { ErrorProps } from '../types/PageError';
import { useEffect, useMemo } from 'react';
import ErrorPage from 'next/error';

export default function DefaultErrorMessage({ error }: ErrorProps) {
  useEffect(() => {
    console.error(error.message);
    console.debug(error);
  }, [error]);

  const statusCode = useMemo(() => {
    switch (error.message) {
      case 'fetch failed':
        return 503;
      case 'Not Found':
        return 404;
      default:
        return 500;
    }
  }, [error.message]);

  return <ErrorPage statusCode={statusCode} />;
}
