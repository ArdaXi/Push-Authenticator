Introduction
============

The challenge-response protocol used by Push Authenticator is simple and based on JSON and OCRA.

JSON is defined in RFC 4627 and OCRA is defined in RFC 6287.

The URL is given to be previously defined.

Example
=======

Request
-------

`url?user=user&client_challenge=123456`

Response
--------

`{"request":"true","response":"123456","challenge":"789012","verification":"345678"}`

Request
-------

`url?user=user&server_challenge=789012&response=912345`

Response
--------

`1`

Protocol
========

First, the client sends a request to the URL provided previously. Two query parameters are added, the previously defined `user`, and a randomly generated `challenge`.

The server responds with a JSON object containing

- a boolean `request` which defines whether there is actually a login request. If this is false, the client aborts with an error.

- a string `response` containing the OCRA response calculated based on the client's challenge.

- a string `challenge` which the client is expected to calculate a response to.

- a string `verification` which the client shows to the user as a means to verify which request the user is approving.

- an optional string `details` which, if included, the user can access with a button if there are doubts. This can include a timestamp and the IP address that initiated the request.

The client then asks the user to approve, and sends a new request to URL. Now, the previous `user` parameter is added, together with the `server_challenge` it has responded to for identification purposes and a `response` parameter containing the calculated response.

The server now responds with a 1 if the request was successful, and the 0 if it wasn't.