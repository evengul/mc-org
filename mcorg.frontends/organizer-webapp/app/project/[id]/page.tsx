import { Fragment } from 'react';
import { CountedTaskResponse, ProjectsService } from '../../../api/projects';
import { PageProps } from '../../../types/PageProps';

interface ProjectProps {
  id: string;
}

const friendlyEnum = (value: string) => {
  return value
    .split('_')
    .map((word) => `${word[0]}${word.toLowerCase().slice(1)}`)
    .join(' ');
};

export default async function Project({ params: { id } }: PageProps<ProjectProps>) {
  const project = await ProjectsService.getProject(id);

  const { done, needed } = project.countedTasks.reduce(
    (prev, curr) => ({ done: curr.done + prev.done, needed: curr.needed + prev.needed }),
    { done: 0, needed: 0 }
  ) ?? {
    done: 0,
    needed: 0,
  };

  const totalTasks = project.tasks.length;

  const completed = project.tasks.filter((task) => task.isDone).length;

  const byType = project.countedTasks.reduce(
    (prev, curr) => {
      if (curr.category) {
        if (prev[curr.category]) {
          return {
            ...prev,
            [curr.category]: [...prev[curr.category], curr],
          };
        }
        return {
          ...prev,
          [curr.category]: [curr],
        };
      } else {
        return {
          ...prev,
          OTHER: [...prev['OTHER'], curr],
        };
      }
    },
    { OTHER: [] } as Record<string, CountedTaskResponse[]>
  );

  return (
    <>
      <h2>{project.name}</h2>
      {totalTasks > 0 && (
        <h3>
          Completed {completed} / {totalTasks} tasks.
        </h3>
      )}
      {needed > 0 && (
        <h3>
          Collected {done} / {needed} items.
        </h3>
      )}
      {Object.entries(byType).map(([type, values]) => (
        <Fragment key={type}>
          <details key={type} title={type}>
            <summary>{friendlyEnum(type)}</summary>
            <ul>
              {values.map((value) => (
                <li key={value.id}>
                  {value.name}: {value.done} / {value.needed}
                </li>
              ))}
            </ul>
          </details>
        </Fragment>
      ))}
    </>
  );
}
