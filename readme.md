# FunMesh (Functions Mesh)

## The question
What to run to play with microservice mesh & Kubernetes?

## The idea
- to run the set of as simple microservices as possible: each microservice will be just a single math function like `_ + _` or `sin _` 
- primary server calls appropriate microservices to calculate requested value of the complex function of 1 variable
- front HTTP page calls primary server to calculate the function values with regulated RPS scaling the load and draw statistics. 
It also may build the function graph
- the primary server & microservices all are the same application that is run with specified role/mode 
- in separate mode primary server & microservice functions are different tasks each listening its own port 
- in combined mode FunMesh is a single task (primary server that calls its endpoints as primitive functions)

## It uses
- Scala 2.13
- tapir asyncFuture with Netty server & sttp client

## "How to run/stop" variants

1. Clone, compile & run. If code is changed then use `assembly` to package all the staff to "uber" (Fat) `funmesh.jar` to [\bin](bin) folder
2. Just download [\bin](bin) folder with "uber" (Fat) [funmesh.jar](bin%2Ffunmesh.jar) and use win `bat` commands 
to run `funmesh` in different modes from terminal. Parameters passed to `bat` commands also will be passed to `funmesh` 
(mode sensitive ones which are specified by the `bat` will be overridden):
- [funmeshCombinedMode.bat](bin%2FfunmeshCombinedMode.bat) runs single server with primary and all primitive functions
- [funmeshSeparateMode.bat](bin%2FfunmeshSeparateMode.bat) runs all servers separately
- [funmeshAllMicrosevices.bat](bin%2FfunmeshAllMicrosevices.bat) runs only microservices with primitive functions. 
Useful when microservices are on a different host
- [funmeshPrimaryServer.bat](bin%2FfunmeshPrimaryServer.bat) runs only primary server in Separate Mode. 
Specify the `msHost` parameter if microservices do not run on `localhost` 
3. In the Docker:
- for combined mode: execute the following in the `bin` folder
```
bin> docker compose -f compose-combined.yaml up -d
```
- for separate mode with primary server also run in the Docker: execute the following in the `bin` folder  (default compose project)
```
bin> docker compose up -d
```
- for separate mode with primary server run at the host
  - run microservices in the Docker: execute the following in the `bin` folder
    ```
    bin> docker compose -f compose-microservices.yaml up -d
    ```
  - and run primary server at the host: execute the following in the `bin` folder
    ```
    bin> funmeshPrimaryServer.bat
    ```
    or just (with optional `headless=true` to detach it)
    ```
    bin> java -jar funmesh.jar roleId=0
    ```
4. To terminate all servers at once call `/shutdown` endpoint of the primary server: it will stop everything run.
By default, http://localhost:8080/shutdown

## Sample function to test all connections
```
(arccos cos x) * [arcsin sin x] * arctg tg x - log e * log10 10 + sqrt 64 / cbrt 64 / 2 - log (e ^ x) / x + abs {-+--sin pi}
```
At `x=1` it should evaluate above function near `0` (actual is `1.2246467991473532e-16`)

## Function Mesh usage

`funmesh [roleId=all|R] [basePort=NNNN] [msHost=DNorIP] [serverHost=DNorIP] [headless=true|false] [?|help]`

Depending on `roleId` parameter the Function Mesh app may be run implementing 3 types of behavior for 2 general modes:
- Single server with complex function & all predefined primitive functions: `roleId=all` (combined mode, default)
- Primary server implementing only complex function that calls primitive functions of microservices: `roleId=0` (separate mode)
- Microservice server that implements one primitive function from the predefined set: `roleId=(1 to 16)` (separate mode)

Primary server (in separate or combined mode) always listens `basePort` (default is `8080`) and
provides `/eval?f=string_with_complex_function&x=Double` endpoint to evaluate `f(x)`.

Primitive function microservice in separate mode listens `basePort+roleId` port providing endpoint with the name of the primitive function
`/<fun_name>?x=Double` to evaluate primitive unary function or `/<fun_name>?x=Double&y=Double` to evaluate binary one.
For example, `/abs?x=-3` and `/add?x=5&y=4`.
The primitive function of microservice is specified by the `roleId` parameter.
In separate mode all servers of the Function Mesh should be run with the same `basePort` for proper function calls.

In combined mode (single server) the evaluation of the complex function calls above endpoints of itself to evaluate primitive
functions (that also gives evaluation of HTTP overhead).

`msHost` parameter defines the common host of all microservice servers and is used by primary server to call microservices
in separate mode. Default is `localhost`.

`serverHost` parameter defines the host on which server accepts connections. Default value is `0.0.0.0` that means
server "listens" for and accepts connections from any IP address. Pass `localhost` if you want to limit server visibility
to host where server is started (in this case external connection to Docker container where funmesh is started will fail).

In `headless` mode server does not use StdIn to wait for exit command (Enter). Default is `false` and the started funmesh will block terminal.
Passing `true` to `headless` starts funmesh in service mode. 

In any mode you may use `/shutdown` endpoint on primary server to stop server and all microservices.

Current microservice roles (and its string function symbols) are:
1. add `+`
2. sub `-`
3. mul `*`
4. div `/`
5. power `^`
6. abs `abs`
7. sqrt `sqrt`
8. cbrt `cbrt`
9. log `log`
10. log10 `log10`
11. sin `sin`
12. cos `cos`
13. tg `tg`
14. arcsin `arcsin`
15. arccos `arccos`
16. arctg `arctg`

Complex string function is case-insensitive and accepts 2 standard constants: `PI` & `E`.
Binary operations are evaluated left to right. Power `^` has the highest priority between binary operations.  
Unary operations (including all unary functions) are evaluated first.

Go to `http://localhost:<port>/docs` to open SwaggerUI. By default, http://localhost:8080/docs for primary server. 

Use `http://localhost:<port>/metrics` to access Prometheus Metrics. By default, http://localhost:8080/metrics for primary server.

Use `http://localhost:<port>/help` to access the used config and this help description and may be used as health check. By default, http://localhost:8080/help for primary server.

Use `http://localhost:<port>/shutdown` to terminate all microservices & primary server itself. By default, http://localhost:8080/shutdown for primary server.


## Versions
| Number | Date        | Changes                                |
|--------|-------------|----------------------------------------|
| 0.1.1  | April, 2024 | initial: app only without service mesh |
