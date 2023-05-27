'use client';
import { ErrorProps } from '../../types/PageError';
import DefaultErrorMessage from '../../components/DefaultErrorMessage';

export default function ProjectsError({ error, reset }: ErrorProps) {
  return <DefaultErrorMessage error={error} reset={reset} />;
}
