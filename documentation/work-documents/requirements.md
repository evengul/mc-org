# MC-Org requirements

## Authentication and authorization
 - A user should be able to sign in using Microsoft. The user ID should be the UUID from the MC account. The username should also be from the MC account. https://wiki.vg/Microsoft_Authentication_Scheme#Authenticate_with_Minecraft

## Start and stop an actual server

### Usecases
 - I want to be able to start a server using the current configuration
 - I want to be able to stop the running server
 - I want to be able to restart the running server
 - I want to be able to change configuration (version, modpack(s) etc) easily
 - I want the server to be persistent across restarts
 - I want to be able to communicate directly with the server using commands

## Resource Directory (mods, datapacks etc) EDIT: Use modrinth API instead of making this ourselves
 - I want to be able to register resources identified by the minecraft version they are usable in, along with a link to download it
 - I want to have these resources auto-update if possible (check github for new downloads? API?)
 - I want to be able to retrieve these resources to enable pushing them to the server-service, so I don't have to reconfigure every time I want to do something else
 - The resources should be packed in "resource packs", which consists of mods, texture packs and data packs. 

## Projects
 - I want to upload litematica-files and convert these into projects. As an MVP, I can upload the material list you can generate through the app
 - A project can have doable and countable tasks
 - A project should have a title, a set of doable and a set of countable tasks
 - I want to create dependencies between projects, so I know what has to be done first, as well as dependencies from tasks to new projects (when you need a farm for a large amount of blocks)
 - I want to view what I have left on a project, especially the countable ones.

## Design
 - I should be able to view, filter and search through an archive of project templates
 - As an admin, I should be able to create new project templates
 - I should be able to place these templates in a 3D space, so that I may plan out the project in a more visual way
 - I should be able to create a roadmap for a collection of projects, with automatic dependencies selected from the project requirements and existing templates
