# FacilityFlow User Guide

No finished product release is available from this branch. The current build
is a development-only Requester form preview; it cannot submit or save requests
and has no login, demo credentials, or request history.

To try the preview, follow the [developer setup](DeveloperGuide.md#setup-and-commands)
and run `./gradlew.bat run` on Windows, or `sh ./gradlew run` on macOS/Linux.

Enter title, description, and location, then select category and reported urgency.
Choose **Check details**. Invalid fields receive guidance, and entered values
remain in the form. Valid details produce a message confirming only validation;
nothing is saved. Closing the window discards all input.

The preview uses the seven initial categories from the
[category catalogue contract](CategoryCatalogue.md). Production catalogue
configuration and migration are not available yet.

Installation from a release, account credentials, request submission, and other
role workflows will be documented when implemented and verified.
