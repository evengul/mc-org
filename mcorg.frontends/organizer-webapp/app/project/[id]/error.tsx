'use client';
import { ErrorProps } from '../../../types/PageError';
import DefaultErrorMessage from '../../../components/DefaultErrorMessage';

export default function ProjectError({ error, reset }: ErrorProps) {
  return <DefaultErrorMessage error={error} reset={reset} />;
}
