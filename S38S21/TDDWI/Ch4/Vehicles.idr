module S38S21.TDDWI.Ch4.Vehicles

data PowerSource = Pedal | Petrol

data Vehicle : PowerSource -> Type where
	Bicycle : Vehicle Pedal
	Car : (fuel : Nat) -> Vehicle Petrol
	Bus : (fuel : Nat) -> Vehicle Petrol

wheelCount : Vehicle power -> Nat
wheelCount Bicycle = 2
wheelCount (Car fuel) = 4
wheelCount (Bus fuel) = 4

refuel : Vehicle Petrol -> Vehicle Petrol
refuel (Car _) = Car 100
refuel (Bus _) = Car 200
refuel Bicycle impossible -- Optional sanity check
