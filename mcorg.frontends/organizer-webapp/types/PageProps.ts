export interface PageProps<T extends Record<never, unknown> = {}> {
  params: T;
  searchParams: Record<string, string>;
}
