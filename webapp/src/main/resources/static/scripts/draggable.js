document.addEventListener('DOMContentLoaded', (event) => {
    const tasks = document.querySelectorAll('.task');
    const columns = document.querySelectorAll('.task-column');

    tasks.forEach(task => {
        task.addEventListener('dragstart', onDragStart);
    });

    columns.forEach(column => {
        column.addEventListener('dragover', onDragOver);
        column.addEventListener('drop', onDrop);
    });
});

function onDragStart(event) {
    event.dataTransfer.setData('text/plain', event.target.id);
    event.dataTransfer.effectAllowed = 'move';
}

function onDragOver(event) {
    event.preventDefault();
    event.dataTransfer.dropEffect = 'move';
}

function onDrop(event) {
    event.preventDefault();
    const id = event.dataTransfer.getData('text/plain');
    const draggableElement = document.getElementById(id);
    const dropzone = event.target.closest('.task-column').querySelector('ul');
    if (dropzone && dropzone.closest("section").id === draggableElement.closest("section").id) {
        dropzone.appendChild(draggableElement);

        // Update the task stage
        const url = new URL(window.location.href);
        const pathSegments = url.pathname.split('/');
        const worldId = pathSegments[3];
        const projectId = pathSegments[5];
        const taskId = draggableElement.id.split('-')[1];
        const stage = dropzone.closest('.task-column').id;
        updateTaskStage(worldId, projectId, taskId, stage)
    }
    event.dataTransfer.clearData();
}

function updateTaskStage(worldId, projectId, taskId, stage) {
    fetch(`${projectId}/tasks/${taskId}/stage?stage=${stage}`, {
        method: 'PATCH',
        body: JSON.stringify({ stage }),
    })
        .then(response => {
            if (!response.ok) {
                throw new Error('Failed to update task stage');
            }
        })
        .catch(error => {
            console.error(error);
        });
}