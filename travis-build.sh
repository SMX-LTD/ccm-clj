#!/bin/sh
set -ex
git clone https://github.com/pcmanus/ccm.git ccm
sudo easy_install pyYaml
sudo easy_install six
cd ccm
sudo ./setup.py install
ccm create ccmcljtestlog -v 2.0.9
cat /home/travis/.ccm/repository/last.log
chmod g-wx,o-wx ~/.python-eggs
