# `b4-devops` ("developer-operations" ... not to be confused with that other thing)

- A tool for highly configurable and composable deployment automation

## Prerequisites

`b4-devops` uses a few standard build and deployment tools. There shouldn't be specific version requirements (anything
recent should probably work), but some installations are easier to set up than others. The following packages are
recommended, but alternatives such as minikube should also be fine if you've already installed them.
 
- maven: `brew install maven` (tested with version 3.6.2)
- helm: `brew install helm` (tested with version 3.0.2)
- docker: for mac workstations, Docker Desktop is recommended: https://docs.docker.com/docker-for-mac/install/ (tested with version 2.1.0.5)
- kubernetes/kubectl: (installed with Docker Desktop)

### Instructions for minikube on Ubuntu

1. Install docker (tested with 19.03.6)
1. Install minikube (tested with version 1.11.0)
1. Login to docker registry (optional)

#### Accessing minikube from host OS

1. Redirect host port to local minikube port.
    ```
    sudo apt install socat
    socat tcp-listen:8003,reuseaddr,fork tcp:<minikube vm ip>:8443
    ```
1. Add iptables rule to allow incoming traffic.
    ```
    sudo iptables -A INPUT -p tcp --dport 8003 -m conntrack --ctstate NEW,ESTABLISHED -j ACCEPT
    sudo iptables -A OUTPUT -p tcp --sport 8003 -m conntrack --ctstate ESTABLISHED -j ACCEPT
    ```
1. Copy kube config to laptop
    ```
    scp $desktop:~/.kube/config ~/.minikube_config
    ```
1. Copy the certs from minikube. You will need:
     - `~/.minikube/ca.crt`
     - `~/.minikube/profiles/minikube/client*`
     
1. Replace the lines in `.minikube_config`
    ```
    apiVersion: v1
    clusters:
    - cluster:
        certificate-authority: <ca.crt location>
        server: https://<your desktop ip>:8003
      name: minikube
    contexts:
    - context:
        cluster: minikube
        user: minikube
      name: minikube
    current-context: minikube
    kind: Config
    preferences: {}
    users:
    - name: minikube
      user:
        client-certificate: <client.crt location>
        client-key: <client.key location>
    ```

## Getting Started

see [../b4](b4)
