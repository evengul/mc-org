function activateTaskButtons() {
  const container = document.getElementById('hidden-task-buttons');

  if (container.classList.contains('hidden')) {
    container.classList.remove('hidden')
    container.classList.add('active')
  } else {
    hideTaskButtons()
  }
}

function hideTaskButtons() {
  const container = document.getElementById('hidden-task-buttons');
  container.classList.remove('active')
  container.classList.add('hidden')
}

document.addEventListener('click', (e) => {
  const container = document.getElementById('hidden-task-buttons');

  if(!container.contains(e.target) && container.id !== e.target.id && e.target.id !== "add-task-button") {
    hideTaskButtons()
  }
})