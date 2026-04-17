module S38S21.TDDWI.Ch4.Directions

data Direction = N|E|S|W

turnClockwise : Direction -> Direction
turnClockwise N = E
turnClockwise E = S
turnClockwise S = W
turnClockwise W = N
