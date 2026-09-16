#!/usr/bin/env bash
# Compiles and runs every problem. Fails fast on a compile or runtime error.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

run() {
  local dir="$1" main="$2"
  echo ""
  echo "############################################################"
  echo "# $dir"
  echo "############################################################"
  cd "$ROOT/$dir"
  rm -rf out
  # shellcheck disable=SC2046
  javac -d out $(find src -name "*.java")
  java -cp out "$main"
  rm -rf out
}

run ParkingLot               parkinglot.Main
run RateLimiter              ratelimiter.Main
run UrlShortner              urlshortener.Main
run EmployeeManagementSystem ems.Main
run TicTacToe                tictactoe.Main
run SnakeAndLadder           snakeandladder.Main
run Splitwise                splitwise.Main
run BookMyShow               bookmyshow.Main
run CabBookingSystem         cab.Main

echo ""
echo "All nine ran successfully."
