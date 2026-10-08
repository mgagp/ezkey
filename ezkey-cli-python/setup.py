"""
Ezkey - Open Source MFA/Passkey Alternative

Copyright (c) 2025 Ezkey contributors
Licensed under the MIT License. See LICENSE file in the project root for full license information.

CLI Component: Setup Script
Description: Python package setup for ezkey CLI
"""

from setuptools import setup, find_packages

with open("README.md", "r", encoding="utf-8") as fh:
    long_description = fh.read()

setup(
    name="ezkey-cli",
    version="1.0.0",
    author="Ezkey contributors",
    description="Command line interface for Ezkey - Open Source MFA/Passkey Alternative",
    long_description=long_description,
    long_description_content_type="text/markdown",
    url="https://github.com/mgagp/ezkey",
    packages=find_packages(),
    classifiers=[
        "Development Status :: 4 - Beta",
        "Intended Audience :: Developers",
        "License :: OSI Approved :: MIT License",
        "Operating System :: OS Independent",
        "Programming Language :: Python :: 3",
        "Programming Language :: Python :: 3.12",
        "Programming Language :: Python :: 3.13",
        "Programming Language :: Python :: 3.14",
    ],
    python_requires=">=3.12",
    # Version floors are tested minimums (install these pins and run tests/unit).
    install_requires=[
        "click>=8.5.0",
        "requests>=2.34.2",
        "colorama>=0.4.6",
        "pyperclip>=1.11.0",
        "textual>=0.38.0",
    ],
    entry_points={
        "console_scripts": [
            "ezkey=ezkey_cli.main:cli",
        ],
    },
    include_package_data=True,
    zip_safe=False,
    package_data={
        '': ['THIRD-PARTY-LICENSES.txt', 'LICENSE', 'README.md'],
    },
)
