#!/bin/sh
set -ex
git clone https://github.com/pcmanus/ccm.git ccm
sudo easy_install pyYaml
sudo easy_install six
cd ccm
sudo ./setup.py install
echo  /home/travis/.ccm/repository/last.log