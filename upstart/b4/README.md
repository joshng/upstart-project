# `b4` (b~uild~)

- A tool for configurable and composable build and deployment automation

## Getting Started

- run the `b4` wrapper-script (located in the `bin` directory at the top of the repo) to have the tool build itself 
and then display some introductory documentation:

```
$ cd $YOUR_REPO_ROOT  # or any directory inside the repo
$ bin/b4
Rebuilding...

Usage: b4 [[-b] | [-c] | [-C]] [[-q] | [-i] | [-v] | [-V]] [-hl]
[more help-text...]
```

You can run this script from anywhere in the source-tree; it will find a parent-directory containing a file called
`B4.conf` to stay oriented, and then all of its tasks will be executed from within that directory.
 
You might want to add that `bin` directory to your shell's *PATH*, or define an alias to invoke
the script from anywhere (for example, I've added `alias b4=~/dev/upstart-project/bin/b4` to my `~/.bashrc`).

### Rebuilding `b4`

The wrapper script will rebuild the java-based tool if it is invoked with `-r`. You may want to do this when
you update your local source-tree, in case the implementation has changed:

```
$ bin/b4 -r
Rebuilding...
```

`-r` can also be combined with other commands/options to run after the build-tool has been rebuilt: `b4 -r deploy/kafka`

**HOWEVER**, note that due to implementation complexities, `-r` must currently appear as the first argument on the command-line, and cannot be combined with other flags

## Conceptual Overview 

### b4: DAGs of Targets and Tasks
`b4` is a generic build and deployment tool that understands graphs of interdependent
`targets` and `tasks`, and allows them to be selectively configured and executed via the command-line. `b4` is
similar to `make`, `ant`, or `maven`, but is intended to be more dynamically flexible, configurable, and customizable
than those build-centric tools, to befit its more "operational" focus.

### Tasks: B4Function Invocations

b4 performs work by invoking `B4Functions`, which are java classes that implement the `B4Function` interface. To
facilitate customization and composability, B4Functions should perform a single logical step in a workflow. 

Each distinct invocation of a function is called a `Task`, and each Task has a name.
 
For example, the task to invoke helm to render a chart for zookeeper might be called `helm/render:zk`, where:

- `helm` is a _`namespace`_
- `helm/render` is the name of the B4Function (implemented by the `HelmRenderFunction` class)
- `zk` is the "instanceId"
- `helm/render:zk` is the Task

Each uniquely-named B4Function invocation will only be performed once for any b4 execution, so you don't need to worry
about overlaps or duplication when composing complex commands.

### Targets: Graphs of Tasks, Defined with HOCON

b4 **`targets`** are defined in a text-file called (by default) `B4.conf`. A target is comprised of a few properties:

- `dependencies`: other targets or tasks which must be executed first (unless they are explicitly _skipped_ -- see below)
- `tasks`: targets or tasks to be executed once the `dependencies` are completed
- `taskConfig`: configuration values to be applied when this target is included by an active command (which may influence **other** targets/tasks)
- `variants`: modified forms of the target, which may add steps or alter configuration


Targets are invoked by listing them on the command-line. For example, to invoke the `deploy/kafka` target:

```
$ b4 deploy/kafka
```

Multiple targets may be listed on a single command-line, and all tasks implied by those targets will be executed
exactly once, and in parallel, as soon as their dependencies are satisfied.

#### Tasks are Targets, Too

Individual tasks may also be invoked via the command-line independently. However, note that unlike full Targets,
individual Tasks do not impose any dependency-ordering on their execution: unless referenced by a Target in the
graph, an independent task will be invoked immediately without waiting for anything else to complete.

## Getting Help

### Listing Available Targets
When invoked without any commands (or with the `-h`/`--help` or `-l`/`--list` flags), the tool will display a list
of available targets.

### Learning About Targets

To see a detailed description of what will happen for a given command-line, prefix it with the `-h` (`--help`) flag:

```
$ b4 -h deploy/kafka
```

This will display some general help-text, along with:
1. a visualization of the _graph_ of actions that would be performed by the listed target(s)
1. a description of each of those actions, documentation for their configuration options,
and the resolved configurations that will be applied.

#### Graphs are Great

Here's an example graph, as rendered (at the time of this writing) for the `deploy/kafka` target :
```
$ b4 -h deploy/kafka

...

Describing command `-h deploy/kafka`:

                           +----------------+
                           | helm/render:zk |
                           | Info DirtyRun  |
                           +----------------+
                                    |
         o--------------------------o
         |
         v
 +---------------+ +-------------------+ +-----------------------------+
 | deploy/k8s:zk | | helm/render:kafka | | file/create-dir:kafkalogdir |
 | Info DirtyRun | |    Info DirtyRun  | |         Info DirtyRun       |
 +---------------+ +-------------------+ +-----------------------------+
         |                          |                   |
         o----------------------o   |    o---------------
                                |   |    |
                                v   v    v
                          +-------------------+
                          | deploy/k8s:kafka  |
                          |    Info DirtyRun  |
                          +-------------------+
```

Reading from top to bottom, this graph depicts that the `helm/render:zk` _task_ will be invoked first (to render the
zookeeper helm-chart). Following the arrow down from that box, we see the `deploy/k8s:zk` task will run next, to apply the 
k8s specs just rendered by helm via your kubectl installation. Meanwhile, the `helm/render:kafka` and
`file/create-dir:kafkalogdir` tasks will be started **in parallel** with `helm/render:zk` (because they have no arrows entering from above).
 
Finally, `deploy/k8s:kafka` will be invoked, and then the `deploy/kafka` _target_ will be complete.

#### Seeing a more complete graph

By default, when describing a command, the displayed graph is simplified to show only the meaningful tasks which perform
real work (omitting the organizational _targets_ that group those tasks together). To show the full graph including
targets, use `-F` (`--full-graph`) along with `-h` (or `-hF`):

```
Describing command `-hF deploy/kafka`:

                     +----------------+
                     | helm/render:zk |
                     | Info DirtyRun  |
                     +----------------+
                              |
            o-----------------o
            |
            v
    +---------------+    +-----------------------------+
    | deploy/k8s:zk |    | file/create-dir:kafkalogdir |
    | Info DirtyRun |    |       Info DirtyRun         |
    +---------------+    +-----------------------------+
        |                 |
        v                 v
 +-------------+ +----------------+ +-------------------+
 |  deploy/zk  | | deploy/log-dir | | helm/render:kafka |
 |Info DirtyRun| | Info DirtyRun  | |   Info DirtyRun   |
 +-------------+ +----------------+ +-------------------+
        |                    |                  |
        o---------------o    |    o--------------
                        |    |    |
                        v    v    v
                  +------------------+
                  | deploy/k8s:kafka |
                  |   Info DirtyRun  |
                  +------------------+
                             |
                             v
                    +-----------------+
                    |  deploy/kafka   |
                    |  Info DirtyRun  |
                    +-----------------+
```

(_tasks_ are identified by a function-name followed by a colon, while _targets_ have no colon.)

### Configuring Specific Tasks

B4 targets are usually comprised of multiple tasks, each of which may accept configuration options.

When describing a target with `-h`, the fully-specified options for each task are displayed after the graph:

```
Describing command `-h deploy/zk`:

 +----------------+
 | helm/render:zk |
 | Info DirtyRun  |
 +----------------+
         |
         v
 +---------------+
 | deploy/k8s:zk |
 | Info DirtyRun |
 +---------------+

Associated targets and their default options:

deploy/k8s

reference defaults & docs
| kubectlExecutable: kubectl  # override to customize name/path for kubectl executable
| // spec: <required>         # path to k8s yaml/json file to deploy

resolved task configs
| deploy/k8s:zk {
|     "kubectlExecutable" : "kubectl",
|     "namespace" : "kafkadev",
|     "spec" : "helmcharts/rendered/kafkadev/zk.yaml",
|     "statefulSet" : {
|         "name" : "zookeeper"
|     }
| }

... (other configs elided) ...
```
 
These options can be modified by adding the task-name to the command-line, then following it with one or more
HOCON-compatible configuration strings prefixed with `--`:

```
Describing command `-h deploy/zk deploy/k8s:zk --spec=zk-test.yaml`:

 +----------------+
 | helm/render:zk |
 | Info DirtyRun  |
 +----------------+
         |
         v
 +---------------+
 | deploy/k8s:zk |
 | Info DirtyRun |
 +---------------+

Associated targets and their default options:

deploy/k8s

reference defaults & docs
| kubectlExecutable: kubectl  # override to customize name/path for kubectl executable
| // spec: <required>         # path to k8s yaml/json file to deploy
| // namespace: <required>

resolved task configs
| deploy/k8s:zk {
|     "kubectlExecutable" : "kubectl",
|     "namespace" : "kafkadev",
|     "spec" : "zk-test.yaml",                <----- ***
|     "statefulSet" : {
|         "name" : "zookeeper"
|     }
| }
```

### Verbosity and Clean Builds
The `Info DirtyRun` lines in the graphs above describe the selected verbosity and build-phase for each task: `Info`
indicates normal verbosity, and `DirtyRun` indicates the task will be executed _without_ invoking its `Clean` process
first, which is the default behavior.
 
These settings can be influenced by adding flags to the command-line. For example, `-c` requests a `CleanRun`
(performing cleanup beforehand), and `-q` (`--quiet`) suppresses normal output (silent unless something
goes wrong):

```
$ b4 -hqc deploy/zk

...

 +----------------+
 | helm/render:zk |
 | Quiet CleanRun |
 +----------------+
         |
         v
 +---------------+
 | deploy/k8s:zk |
 |Quiet CleanRun |
 +---------------+
```

### Global vs. Local flags

As depicted above, verbosity and execution flags (`-v`, `-c`) that are passed _before_ any target-names apply to **all**
tasks in the graph. These flags can also be positioned _after_ a target-name, to apply only to the tasks associated with
that target. Here, all tasks associated with `deployzk` will perform a `CleanRun`, but not the others:

```
Describing command `-hF deploy/kafka deploy/zk -c`:

                     +----------------+
                     | helm/render:zk |
                     | Info CleanRun  |
                     +----------------+
                              |
            o-----------------o
            |
            v
    +---------------+    +-----------------------------+
    | deploy/k8s:zk |    | file/create-dir:kafkalogdir |
    | Info CleanRun |    |       Info DirtyRun         |
    +---------------+    +-----------------------------+
        |                 |
        v                 v
 +-------------+ +----------------+ +-------------------+
 |  deploy/zk  | | deploy/log-dir | | helm/render:kafka |
 |Info CleanRun| | Info DirtyRun  | |   Info DirtyRun   |
 +-------------+ +----------------+ +-------------------+
        |                    |                  |
        o---------------o    |    o--------------
                        |    |    |
                        v    v    v
                  +------------------+
                  | deploy/k8s:kafka |
                  |   Info DirtyRun  |
                  +------------------+
                             |
                             v
                    +-----------------+
                    |  deploy/kafka   |
                    |  Info DirtyRun  |
                    +-----------------+
```

## Cleaning is Backwards

When `CleanRun` or `CleanOnly` tasks are included in the graph, their cleanup routines are invoked
first, _in reverse-dependency-order_ (ie, with all of the arrows reversed), before any `Run` actions are initiated.
This is to ensure that operational dependencies aren't torn down while dependent components are still running.

## Controlling Execution: skip or execute specific targets

Imagine you've deployed a collection of artifacts, and now you want to redeploy a subset of them
-- perhaps with a different configuration, or to test some local implementation changes.

For example, if you've already run `deploy/kafka` as depicted by the first graph above, then you should have a
zookeeper cluster running in your configured kubernetes cluster.
 
If you now ran `deploy/kafka` again, everything would be torn down and recreated from scratch (including the zk
cluster), which isn't always what you want: perhaps you only want to update kafka, but leave the zk nodes alone.
 
In other words, you want to _skip_ the zookeeper deployment.

b4 supports this by allowing you to `--skip` (`-s`) targets and tasks whose names match a pattern that you
provide.

In this case, you could run `b4 -s zk deploy/kafka` to skip any steps matching the string *`zk`*.
 
Let's inspect the graph for this modified command:

```
Describing command `-h -s zk deploy/kafka`:

 +-----------------------------+ +-------------------+
 | file/create-dir:kafkalogdir | | helm/render:kafka |
 |       Info DirtyRun         | |    Info DirtyRun  |
 +-----------------------------+ +-------------------+
                        |                  |
                        |      o-----------o
                        |      |
                        v      v
                +------------------+
                | deploy/k8s:kafka |
                |   Info DirtyRun  |
                +------------------+
``` 

This plan includes the steps from `deploy/kafka`, _except_ for those with `zk` in their names. We've pruned the graph to
only include the steps we wanted.

(Note that if a target is `--skipped`, then its dependencies -- anything that would have run before it -- are also
skipped if they aren't needed by another target in the graph.)

`-s`/`--skip` can also be repeated in a command to skip multiple patterns; all matching targets and their dependencies
will be pruned.

### --skip vs. --execute

Conversely, instead of skipping steps, we may instead `--execute` (`-e`) only the steps that match a desired
pattern.

`--execute` and `--skip` may also be combined to select very specific criteria: only targets which are not `skipped` AND
which match an `execute` pattern will be invoked.
