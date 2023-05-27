import { ProjectsService } from '../../api/projects';
import Link from 'next/link';

export default async function Project() {
  const { projects } = await ProjectsService.get();

  return (
      <ul>
        {projects.map((project) => (
            <li key={project.id}>
              <Link href={`/project/${project.id}`}>{project.name}</Link>
            </li>
        ))}
      </ul>
  );
}
