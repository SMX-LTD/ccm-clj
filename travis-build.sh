#!/bin/sh
set -ex
git clone https://github.com/pcmanus/ccm.git
sudo easy_install pyYaml
sudo easy_install six
sudo ./setup.py install
